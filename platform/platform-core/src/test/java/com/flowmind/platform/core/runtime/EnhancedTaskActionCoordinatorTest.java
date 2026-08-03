package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.AttachmentTemplateCheckResult;
import com.flowmind.platform.api.dto.DirectSendContextDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.TaskActionResult;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.request.AddSignRequest;
import com.flowmind.platform.api.request.DelegateTaskRequest;
import com.flowmind.platform.api.request.DirectSendRequest;
import com.flowmind.platform.api.request.RejectTaskRequest;
import com.flowmind.platform.api.request.ReturnTaskRequest;
import com.flowmind.platform.api.request.TransferTaskRequest;
import com.flowmind.platform.api.request.WithdrawTaskRequest;
import com.flowmind.platform.api.service.CallbackService;
import com.flowmind.platform.api.service.AttachmentService;
import com.flowmind.platform.api.spi.OrganizationProvider;
import com.flowmind.platform.core.audit.AuditLogCommand;
import com.flowmind.platform.core.audit.AuditLogWriter;
import com.flowmind.platform.core.definition.OperationIdempotencyDecision;
import com.flowmind.platform.core.definition.OperationIdempotencyDecisionType;
import com.flowmind.platform.core.task.HistoryArchiveCommand;
import com.flowmind.platform.core.task.HistoryTaskWriter;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.entity.ProcessTaskGroupEntity;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessHistoryTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import com.flowmind.platform.persistence.repository.TaskGroupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** M5 增强动作协调器的关键回归测试。 */
class EnhancedTaskActionCoordinatorTest {

    @Test
    void rejectRejectsTargetOutsideConfiguredRuleBeforeCompletingTask() {
        Fixture fixture = fixture(ActionTypeEnum.REJECT);
        RejectTaskRequest request = taskRequest(new RejectTaskRequest(), "op-reject", fixture.task, fixture.operator);
        request.setTargetNodeCode("finance");
        fixture.definition.setNodes(java.util.Arrays.asList(userNode("manager", null), userNode("finance",
                "{\"taskActionRules\":{\"reject\":{\"enabled\":true,\"targetNodeCodes\":[\"starter\"]}}}")));

        RuntimeValidationException error = assertThrows(RuntimeValidationException.class,
                () -> fixture.coordinator.reject(request));
        assertEquals(RuntimeErrorCodes.REJECT_TARGET_NOT_ALLOWED, error.getErrorCode());
        verify(fixture.tasks, never()).complete(any(String.class), any(Long.class));
    }

    @Test
    void rejectRejectsTargetThatWasNotPassedByInstanceBeforeCompletingTask() {
        Fixture fixture = fixture(ActionTypeEnum.REJECT);
        RejectTaskRequest request = taskRequest(new RejectTaskRequest(), "op-reject-unpassed",
                fixture.task, fixture.operator);
        request.setTargetNodeCode("finance");
        fixture.definition.setNodes(java.util.Arrays.asList(userNode("manager",
                "{\"taskActionRules\":{\"reject\":{\"enabled\":true,\"targetNodeCodes\":[\"finance\"]}}}"),
                userNode("finance", null)));
        when(fixture.histories.findByInstanceId("instance-1")).thenReturn(java.util.Collections.singletonList(
                history("manager-history", "user-a", "manager", ActionTypeEnum.APPROVE.name(), null)));

        RuntimeValidationException error = assertThrows(RuntimeValidationException.class,
                () -> fixture.coordinator.reject(request));

        assertEquals(RuntimeErrorCodes.REJECT_TARGET_NOT_ALLOWED, error.getErrorCode());
        assertEquals("未通过该节点，请重新选择", error.getMessage());
        verify(fixture.tasks, never()).complete(any(String.class), any(Long.class));
        verify(fixture.historyWriter, never()).archive(any(HistoryArchiveCommand.class));
    }

    @Test
    void rejectCompletesSourceArchivesRelationAndCreatesTargetTasks() {
        Fixture fixture = fixture(ActionTypeEnum.REJECT);
        RejectTaskRequest request = taskRequest(new RejectTaskRequest(), "op-reject-success", fixture.task, fixture.operator);
        request.setTargetNodeCode("finance");
        fixture.definition.setNodes(java.util.Arrays.asList(userNode("manager",
                "{\"taskActionRules\":{\"reject\":{\"enabled\":true,\"targetNodeCodes\":[\"finance\"]}}}"),
                userNode("finance", null)));
        ProcessHistoryTaskEntity archived = history("reject-history", "user-a", "manager", ActionTypeEnum.REJECT.name(), "{}");
        when(fixture.histories.findByInstanceId("instance-1")).thenReturn(java.util.Collections.singletonList(
                history("finance-history", "finance-user", "finance", ActionTypeEnum.APPROVE.name(), null)));
        allowReachable(fixture, "finance");
        when(fixture.tasks.complete("task-1", 3L)).thenReturn(1);
        when(fixture.historyWriter.archive(any(HistoryArchiveCommand.class))).thenReturn(archived);
        when(fixture.histories.updateExtraJson(eq("reject-history"), any(String.class))).thenReturn(1);
        when(fixture.advancer.advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("finance"), eq(null), eq(null), eq(null)))
                .thenReturn(new RuntimeAdvanceResult());

        TaskActionResult result = fixture.coordinator.reject(request);
        assertNotNull(result);
        verify(fixture.tasks).complete("task-1", 3L);
        verify(fixture.histories).updateExtraJson(eq("reject-history"), any(String.class));
        verify(fixture.advancer).advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("finance"), eq(null), eq(null), eq(null));
    }

    @Test
    void parallelBranchRejectKeepsParallelContextWhenTargetIsInSameBranch() {
        Fixture fixture = fixture(ActionTypeEnum.REJECT);
        fixture.task.setTaskGroupId("parallel-1");
        fixture.task.setBranchKey("branch-a");
        RejectTaskRequest request = taskRequest(new RejectTaskRequest(), "op-parallel-branch-reject",
                fixture.task, fixture.operator);
        request.setTargetNodeCode("apply");
        fixture.definition.setNodes(java.util.Arrays.asList(
                gatewayNode("split", NodeTypeEnum.PARALLEL_SPLIT_GATEWAY, "join"),
                gatewayNode("join", NodeTypeEnum.PARALLEL_JOIN_GATEWAY, "split"),
                userNode("apply", null),
                userNode("review", null),
                userNode("other", null),
                userNode("manager",
                        "{\"taskActionRules\":{\"reject\":{\"enabled\":true,\"targetNodeCodes\":[\"apply\"]}}}")));
        fixture.definition.setEdges(java.util.Arrays.asList(
                edge("branch-a", "split", "apply"),
                edge("apply-review", "apply", "review"),
                edge("review-manager", "review", "manager"),
                edge("manager-join", "manager", "join"),
                edge("branch-b", "split", "other"),
                edge("other-join", "other", "join")));
        when(fixture.histories.findByInstanceId("instance-1")).thenReturn(java.util.Collections.singletonList(
                history("apply-history", "apply-user", "apply", ActionTypeEnum.APPROVE.name(), null)));
        allowReachable(fixture, "apply");
        ProcessTaskGroupEntity parallel = parallelGroup("parallel-1");
        when(fixture.groups.findById("parallel-1")).thenReturn(parallel);
        when(fixture.tasks.complete("task-1", 3L)).thenReturn(1);
        when(fixture.historyWriter.archive(any(HistoryArchiveCommand.class)))
                .thenReturn(history("reject-history", "user-a", "manager", ActionTypeEnum.REJECT.name(), "{}"));
        when(fixture.histories.updateExtraJson(eq("reject-history"), any(String.class))).thenReturn(1);
        when(fixture.advancer.advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("apply"),
                eq("parallel-1"), eq("branch-a"), eq(null))).thenReturn(new RuntimeAdvanceResult());

        TaskActionResult result = fixture.coordinator.reject(request);

        assertNotNull(result);
        verify(fixture.advancer).advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("apply"),
                eq("parallel-1"), eq("branch-a"), eq(null));
        verify(fixture.groups, never()).cancel(any(String.class), any(Long.class));
        verify(fixture.tasks, never()).findOpenByParallelContext(any(String.class), any(String.class));
    }

    @Test
    void parallelRejectExitsAndCancelsWholeParallelContextWhenTargetIsOutside() {
        Fixture fixture = fixture(ActionTypeEnum.REJECT);
        fixture.task.setTaskGroupId("parallel-1");
        fixture.task.setBranchKey("branch-a");
        RejectTaskRequest request = taskRequest(new RejectTaskRequest(), "op-parallel-exit-reject",
                fixture.task, fixture.operator);
        request.setTargetNodeCode("starter");
        fixture.definition.setNodes(java.util.Arrays.asList(
                userNode("starter", null),
                gatewayNode("split", NodeTypeEnum.PARALLEL_SPLIT_GATEWAY, "join"),
                gatewayNode("join", NodeTypeEnum.PARALLEL_JOIN_GATEWAY, "split"),
                userNode("manager",
                        "{\"taskActionRules\":{\"reject\":{\"enabled\":true,\"targetNodeCodes\":[\"starter\"]}}}"),
                userNode("other", null)));
        fixture.definition.setEdges(java.util.Arrays.asList(
                edge("branch-a", "split", "manager"),
                edge("manager-join", "manager", "join"),
                edge("branch-b", "split", "other"),
                edge("other-join", "other", "join")));
        when(fixture.histories.findByInstanceId("instance-1")).thenReturn(java.util.Collections.singletonList(
                history("starter-history", "starter-user", "starter", ActionTypeEnum.APPROVE.name(), null)));
        allowReachable(fixture, "starter");
        ProcessTaskGroupEntity parallel = parallelGroup("parallel-1");
        ProcessTaskGroupEntity child = new ProcessTaskGroupEntity();
        child.setId("child-1");
        child.setParentGroupId("parallel-1");
        child.setGroupType("COUNTERSIGN");
        child.setGroupStatus("ACTIVE");
        child.setLockVersion(Long.valueOf(5));
        ProcessActiveTaskEntity sibling = task();
        sibling.setId("task-2");
        sibling.setTaskGroupId("child-1");
        sibling.setBranchKey("branch-b");
        sibling.setLockVersion(Long.valueOf(1));
        when(fixture.groups.findById("parallel-1")).thenReturn(parallel);
        when(fixture.tasks.complete("task-1", 3L)).thenReturn(1);
        when(fixture.groups.cancel("parallel-1", 2L)).thenReturn(1);
        when(fixture.groups.findActiveChildren("parallel-1"))
                .thenReturn(java.util.Collections.singletonList(child));
        when(fixture.groups.cancel("child-1", 5L)).thenReturn(1);
        when(fixture.tasks.findOpenByParallelContext("parallel-1", "task-1"))
                .thenReturn(java.util.Collections.singletonList(sibling));
        when(fixture.tasks.cancel("task-2", 1L)).thenReturn(1);
        when(fixture.historyWriter.archive(any(HistoryArchiveCommand.class))).thenReturn(
                history("reject-history", "user-a", "manager", ActionTypeEnum.REJECT.name(), "{}"),
                history("cancel-history", "user-b", "other", ActionTypeEnum.CANCEL.name(), "{}"));
        when(fixture.histories.updateExtraJson(eq("reject-history"), any(String.class))).thenReturn(1);
        when(fixture.advancer.advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("starter"),
                eq(null), eq(null), eq(null))).thenReturn(new RuntimeAdvanceResult());

        TaskActionResult result = fixture.coordinator.reject(request);

        assertEquals(2, result.getArchivedTasks().size());
        verify(fixture.groups).cancel("parallel-1", 2L);
        verify(fixture.groups).cancel("child-1", 5L);
        verify(fixture.tasks).cancel("task-2", 1L);
        verify(fixture.advancer).advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("starter"),
                eq(null), eq(null), eq(null));
    }

    @Test
    void parallelRejectToAnotherBranchReentersParallelGatewayAfterCancelingGroup() {
        Fixture fixture = fixture(ActionTypeEnum.REJECT);
        fixture.task.setTaskGroupId("parallel-1");
        fixture.task.setBranchKey("branch-a");
        RejectTaskRequest request = taskRequest(new RejectTaskRequest(), "op-parallel-cross-branch-reject",
                fixture.task, fixture.operator);
        request.setTargetNodeCode("other");
        fixture.definition.setNodes(java.util.Arrays.asList(
                gatewayNode("split", NodeTypeEnum.PARALLEL_SPLIT_GATEWAY, "join"),
                gatewayNode("join", NodeTypeEnum.PARALLEL_JOIN_GATEWAY, "split"),
                userNode("manager",
                        "{\"taskActionRules\":{\"reject\":{\"enabled\":true,\"targetNodeCodes\":[\"other\"]}}}"),
                userNode("other", null)));
        fixture.definition.setEdges(java.util.Arrays.asList(
                edge("branch-a", "split", "manager"),
                edge("manager-join", "manager", "join"),
                edge("branch-b", "split", "other"),
                edge("other-join", "other", "join")));
        when(fixture.histories.findByInstanceId("instance-1")).thenReturn(java.util.Collections.singletonList(
                history("other-history", "other-user", "other", ActionTypeEnum.APPROVE.name(), null)));
        allowReachable(fixture, "other");
        ProcessTaskGroupEntity parallel = parallelGroup("parallel-1");
        when(fixture.groups.findById("parallel-1")).thenReturn(parallel);
        when(fixture.tasks.complete("task-1", 3L)).thenReturn(1);
        when(fixture.groups.cancel("parallel-1", 2L)).thenReturn(1);
        when(fixture.groups.findActiveChildren("parallel-1"))
                .thenReturn(java.util.Collections.<ProcessTaskGroupEntity>emptyList());
        when(fixture.tasks.findOpenByParallelContext("parallel-1", "task-1"))
                .thenReturn(java.util.Collections.<ProcessActiveTaskEntity>emptyList());
        when(fixture.historyWriter.archive(any(HistoryArchiveCommand.class)))
                .thenReturn(history("reject-history", "user-a", "manager", ActionTypeEnum.REJECT.name(), "{}"));
        when(fixture.histories.updateExtraJson(eq("reject-history"), any(String.class))).thenReturn(1);
        when(fixture.advancer.advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("split"),
                eq(null), eq(null), eq(null))).thenReturn(new RuntimeAdvanceResult());

        TaskActionResult result = fixture.coordinator.reject(request);

        assertNotNull(result);
        verify(fixture.groups).cancel("parallel-1", 2L);
        verify(fixture.advancer).advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("split"),
                eq(null), eq(null), eq(null));
        verify(fixture.advancer, never()).advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("other"),
                any(String.class), any(String.class), any(RuntimeAdvancePreparation.class));
    }

    @Test
    void serialRejectIntoParallelStartsWholeParallelGateway() {
        Fixture fixture = fixture(ActionTypeEnum.REJECT);
        RejectTaskRequest request = taskRequest(new RejectTaskRequest(), "op-enter-parallel-reject",
                fixture.task, fixture.operator);
        request.setTargetNodeCode("parallel-review");
        fixture.definition.setNodes(java.util.Arrays.asList(
                userNode("manager",
                        "{\"taskActionRules\":{\"reject\":{\"enabled\":true,\"targetNodeCodes\":[\"parallel-review\"]}}}"),
                gatewayNode("split", NodeTypeEnum.PARALLEL_SPLIT_GATEWAY, "join"),
                gatewayNode("join", NodeTypeEnum.PARALLEL_JOIN_GATEWAY, "split"),
                userNode("parallel-review", null),
                userNode("parallel-finance", null)));
        fixture.definition.setEdges(java.util.Arrays.asList(
                edge("branch-a", "split", "parallel-review"),
                edge("review-join", "parallel-review", "join"),
                edge("branch-b", "split", "parallel-finance"),
                edge("finance-join", "parallel-finance", "join")));
        when(fixture.histories.findByInstanceId("instance-1")).thenReturn(java.util.Collections.singletonList(
                history("parallel-review-history", "review-user", "parallel-review",
                        ActionTypeEnum.APPROVE.name(), null)));
        allowReachable(fixture, "parallel-review");
        when(fixture.tasks.complete("task-1", 3L)).thenReturn(1);
        when(fixture.historyWriter.archive(any(HistoryArchiveCommand.class)))
                .thenReturn(history("reject-history", "user-a", "manager", ActionTypeEnum.REJECT.name(), "{}"));
        when(fixture.histories.updateExtraJson(eq("reject-history"), any(String.class))).thenReturn(1);
        when(fixture.advancer.advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("split"),
                eq(null), eq(null), eq(null))).thenReturn(new RuntimeAdvanceResult());

        TaskActionResult result = fixture.coordinator.reject(request);

        assertNotNull(result);
        verify(fixture.advancer).advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("split"),
                eq(null), eq(null), eq(null));
        verify(fixture.advancer, never()).advanceToNode(eq(fixture.instance), eq(fixture.definition),
                eq("parallel-review"), any(String.class), any(String.class), any(RuntimeAdvancePreparation.class));
    }

    @Test
    void rejectRejectsPassedTargetThatCurrentConditionsCannotReachBeforeCompletingTask() {
        Fixture fixture = fixture(ActionTypeEnum.REJECT);
        RejectTaskRequest request = taskRequest(new RejectTaskRequest(), "op-reject-unreachable",
                fixture.task, fixture.operator);
        request.setTargetNodeCode("finance");
        fixture.definition.setNodes(java.util.Arrays.asList(userNode("manager",
                "{\"taskActionRules\":{\"reject\":{\"enabled\":true,\"targetNodeCodes\":[\"finance\"]}}}"),
                userNode("finance", null), userNode("legal", null)));
        when(fixture.histories.findByInstanceId("instance-1")).thenReturn(java.util.Collections.singletonList(
                history("finance-history", "finance-user", "finance", ActionTypeEnum.APPROVE.name(), null)));
        allowReachable(fixture, "legal");

        RuntimeValidationException error = assertThrows(RuntimeValidationException.class,
                () -> fixture.coordinator.reject(request));

        assertEquals(RuntimeErrorCodes.REJECT_TARGET_NOT_ALLOWED, error.getErrorCode());
        assertEquals("驳回目标节点当前条件不可达，请重新选择", error.getMessage());
        verify(fixture.tasks, never()).complete(any(String.class), any(Long.class));
        verify(fixture.historyWriter, never()).archive(any(HistoryArchiveCommand.class));
    }

    @Test
    void countersignRejectCancelsGroupAndOpenSiblingsBeforeRecreatingTarget() {
        Fixture fixture = fixture(ActionTypeEnum.REJECT);
        fixture.task.setTaskGroupId("group-1");
        RejectTaskRequest request = taskRequest(new RejectTaskRequest(), "op-countersign-reject",
                fixture.task, fixture.operator);
        request.setTargetNodeCode("finance");
        ProcessNodeDTO manager = userNode("manager",
                "{\"taskActionRules\":{\"reject\":{\"enabled\":true,\"targetNodeCodes\":[\"finance\"]}}}");
        manager.setMultiInstanceMode(MultiInstanceModeEnum.COUNTERSIGN);
        fixture.definition.setNodes(java.util.Arrays.asList(manager, userNode("finance", null)));
        when(fixture.histories.findByInstanceId("instance-1")).thenReturn(java.util.Collections.singletonList(
                history("finance-history", "finance-user", "finance", ActionTypeEnum.APPROVE.name(), null)));
        allowReachable(fixture, "finance");
        ProcessTaskGroupEntity group = new ProcessTaskGroupEntity();
        group.setId("group-1");
        group.setInstanceId("instance-1");
        group.setNodeCode("manager");
        group.setGroupType("COUNTERSIGN");
        group.setGroupStatus("ACTIVE");
        group.setLockVersion(Long.valueOf(2));
        group.setBranchStateJson("{}");
        ProcessActiveTaskEntity sibling = task();
        sibling.setId("task-2");
        sibling.setTaskGroupId("group-1");
        sibling.setLockVersion(Long.valueOf(1));
        when(fixture.groups.findById("group-1")).thenReturn(group);
        when(fixture.tasks.complete("task-1", 3L)).thenReturn(1);
        when(fixture.groups.cancel("group-1", 2L)).thenReturn(1);
        when(fixture.tasks.findOpenByTaskGroupId("group-1"))
                .thenReturn(java.util.Collections.singletonList(sibling));
        when(fixture.tasks.cancel("task-2", 1L)).thenReturn(1);
        when(fixture.historyWriter.archive(any(HistoryArchiveCommand.class))).thenReturn(
                history("rejected", "user-a", "manager", ActionTypeEnum.REJECT.name(), "{}"),
                history("canceled", "user-a", "manager", ActionTypeEnum.CANCEL.name(), "{}"));
        when(fixture.histories.updateExtraJson(eq("rejected"), any(String.class))).thenReturn(1);
        when(fixture.advancer.advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("finance"),
                eq(null), eq(null), eq(null))).thenReturn(new RuntimeAdvanceResult());

        TaskActionResult result = fixture.coordinator.reject(request);

        assertEquals(2, result.getArchivedTasks().size());
        verify(fixture.groups).cancel("group-1", 2L);
        verify(fixture.tasks).cancel("task-2", 1L);
    }

    @Test
    void countersignRejectInsideParallelBranchIsRejectedBeforeTaskMutation() {
        Fixture fixture = fixture(ActionTypeEnum.REJECT);
        fixture.task.setTaskGroupId("group-1");
        fixture.task.setBranchKey("branch-a");
        RejectTaskRequest request = taskRequest(new RejectTaskRequest(), "op-parallel-countersign-reject",
                fixture.task, fixture.operator);
        request.setTargetNodeCode("finance");
        ProcessNodeDTO manager = userNode("manager",
                "{\"taskActionRules\":{\"reject\":{\"enabled\":true,\"targetNodeCodes\":[\"finance\"]}}}");
        manager.setMultiInstanceMode(MultiInstanceModeEnum.COUNTERSIGN);
        fixture.definition.setNodes(java.util.Arrays.asList(manager, userNode("finance", null)));
        when(fixture.histories.findByInstanceId("instance-1")).thenReturn(java.util.Collections.singletonList(
                history("finance-history", "finance-user", "finance", ActionTypeEnum.APPROVE.name(), null)));
        allowReachable(fixture, "finance");
        ProcessTaskGroupEntity group = new ProcessTaskGroupEntity();
        group.setId("group-1");
        group.setInstanceId("instance-1");
        group.setNodeCode("manager");
        group.setGroupType("COUNTERSIGN");
        group.setGroupStatus("ACTIVE");
        group.setLockVersion(Long.valueOf(2));
        group.setBranchStateJson("{}");
        group.setParentGroupId("parallel-1");
        group.setParentBranchKey("branch-a");
        when(fixture.groups.findById("group-1")).thenReturn(group);

        RuntimeValidationException error = assertThrows(RuntimeValidationException.class,
                () -> fixture.coordinator.reject(request));

        assertEquals(RuntimeErrorCodes.GROUPED_TASK_ACTION_NOT_SUPPORTED, error.getErrorCode());
        verify(fixture.tasks, never()).complete(any(String.class), any(Long.class));
    }

    @Test
    void countersignRejectStopsWhenSiblingCancelLosesTaskCas() {
        Fixture fixture = fixture(ActionTypeEnum.REJECT);
        fixture.task.setTaskGroupId("group-1");
        RejectTaskRequest request = taskRequest(new RejectTaskRequest(), "op-countersign-reject-conflict",
                fixture.task, fixture.operator);
        request.setTargetNodeCode("finance");
        ProcessNodeDTO manager = userNode("manager",
                "{\"taskActionRules\":{\"reject\":{\"enabled\":true,\"targetNodeCodes\":[\"finance\"]}}}");
        manager.setMultiInstanceMode(MultiInstanceModeEnum.COUNTERSIGN);
        fixture.definition.setNodes(java.util.Arrays.asList(manager, userNode("finance", null)));
        when(fixture.histories.findByInstanceId("instance-1")).thenReturn(java.util.Collections.singletonList(
                history("finance-history", "finance-user", "finance", ActionTypeEnum.APPROVE.name(), null)));
        allowReachable(fixture, "finance");
        ProcessTaskGroupEntity group = new ProcessTaskGroupEntity();
        group.setId("group-1");
        group.setInstanceId("instance-1");
        group.setNodeCode("manager");
        group.setGroupType("COUNTERSIGN");
        group.setGroupStatus("ACTIVE");
        group.setLockVersion(Long.valueOf(2));
        group.setBranchStateJson("{}");
        ProcessActiveTaskEntity sibling = task();
        sibling.setId("task-2");
        sibling.setTaskGroupId("group-1");
        sibling.setLockVersion(Long.valueOf(1));
        when(fixture.groups.findById("group-1")).thenReturn(group);
        when(fixture.tasks.complete("task-1", 3L)).thenReturn(1);
        when(fixture.groups.cancel("group-1", 2L)).thenReturn(1);
        when(fixture.tasks.findOpenByTaskGroupId("group-1"))
                .thenReturn(java.util.Collections.singletonList(sibling));
        when(fixture.historyWriter.archive(any(HistoryArchiveCommand.class)))
                .thenReturn(history("rejected", "user-a", "manager", ActionTypeEnum.REJECT.name(), "{}"));

        RuntimeStateException error = assertThrows(RuntimeStateException.class,
                () -> fixture.coordinator.reject(request));

        assertEquals(RuntimeErrorCodes.TASK_CONCURRENT_MODIFIED, error.getErrorCode());
        verify(fixture.advancer, never()).advanceToNode(any(ProcessInstanceEntity.class),
                any(ProcessDefinitionDetailDTO.class), any(String.class), any(String.class), any(String.class),
                any(RuntimeAdvancePreparation.class));
    }

    @Test
    void returnToStarterFailsWhenNoStarterHistoryExists() {
        Fixture fixture = fixture(ActionTypeEnum.RETURN);
        ReturnTaskRequest request = taskRequest(new ReturnTaskRequest(), "op-return", fixture.task, fixture.operator);
        when(fixture.histories.findLatestByInstanceAndActions(eq("instance-1"), eq(ActionTypeEnum.SEND.name())))
                .thenReturn(java.util.Collections.<ProcessHistoryTaskEntity>emptyList());

        RuntimeStateException error = assertThrows(RuntimeStateException.class,
                () -> fixture.coordinator.returnToStarter(request));
        assertEquals(RuntimeErrorCodes.WITHDRAW_HISTORY_NOT_FOUND, error.getErrorCode());
        verify(fixture.tasks, never()).complete(any(String.class), any(Long.class));
    }

    @Test
    void returnToStarterCompletesCurrentTaskAndAdvancesToStarterHistoryNode() {
        Fixture fixture = fixture(ActionTypeEnum.RETURN);
        ReturnTaskRequest request = taskRequest(new ReturnTaskRequest(), "op-return-success", fixture.task, fixture.operator);
        ProcessNodeDTO starter = userNode("starter", ApproverRuleTypeEnum.STARTER, null);
        fixture.definition.setNodes(java.util.Arrays.asList(starter, userNode("manager", null)));
        ProcessHistoryTaskEntity target = history("starter-history", "user-a", "starter", ActionTypeEnum.SEND.name(), null);
        ProcessHistoryTaskEntity archived = history("return-history", "user-a", "manager", ActionTypeEnum.RETURN.name(), "{}");
        when(fixture.histories.findLatestByInstanceAndActions(eq("instance-1"), eq(ActionTypeEnum.SEND.name())))
                .thenReturn(java.util.Collections.singletonList(target));
        when(fixture.tasks.complete("task-1", 3L)).thenReturn(1);
        when(fixture.historyWriter.archive(any(HistoryArchiveCommand.class))).thenReturn(archived);
        when(fixture.advancer.advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("starter"), eq(null), eq(null), eq(null)))
                .thenReturn(new RuntimeAdvanceResult());

        TaskActionResult result = fixture.coordinator.returnToStarter(request);
        assertNotNull(result);
        verify(fixture.tasks).complete("task-1", 3L);
        verify(fixture.advancer).advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("starter"), eq(null), eq(null), eq(null));
    }

    @Test
    void withdrawRejectsOperatorWhoDidNotHandlePreviousTask() {
        Fixture fixture = fixture(ActionTypeEnum.WITHDRAW);
        WithdrawTaskRequest request = taskRequest(new WithdrawTaskRequest(), "op-withdraw", fixture.task, fixture.operator);
        ProcessHistoryTaskEntity previous = history("previous", "reviewer", "manager", ActionTypeEnum.APPROVE.name(), null);
        when(fixture.tasks.countOpenByInstanceId("instance-1")).thenReturn(1L);
        when(fixture.histories.findLatestByInstanceAndActions(eq("instance-1"), any(String.class), any(String.class),
                any(String.class), any(String.class), any(String.class))).thenReturn(java.util.Collections.singletonList(previous));
        fixture.definition.setNodes(java.util.Collections.singletonList(userNode("manager", null)));

        RuntimeValidationException error = assertThrows(RuntimeValidationException.class,
                () -> fixture.coordinator.withdraw(request));
        assertEquals(RuntimeErrorCodes.WITHDRAW_PERMISSION_DENIED, error.getErrorCode());
        verify(fixture.tasks, never()).cancel(any(String.class), any(Long.class));
    }

    @Test
    void withdrawCancelsCurrentTaskAndRecreatesPreviousHandlerNode() {
        Fixture fixture = fixture(ActionTypeEnum.WITHDRAW);
        WithdrawTaskRequest request = taskRequest(new WithdrawTaskRequest(), "op-withdraw-success", fixture.task, fixture.operator);
        fixture.definition.setNodes(java.util.Collections.singletonList(userNode("manager", null)));
        ProcessHistoryTaskEntity previous = history("previous-success", "user-a", "manager", ActionTypeEnum.APPROVE.name(), null);
        ProcessHistoryTaskEntity archived = history("withdraw-history", "user-a", "reviewer", ActionTypeEnum.WITHDRAW.name(), "{}");
        when(fixture.tasks.countOpenByInstanceId("instance-1")).thenReturn(1L);
        when(fixture.histories.findLatestByInstanceAndActions(eq("instance-1"), any(String.class), any(String.class),
                any(String.class), any(String.class), any(String.class))).thenReturn(java.util.Collections.singletonList(previous));
        when(fixture.tasks.cancel("task-1", 3L)).thenReturn(1);
        when(fixture.historyWriter.archive(any(HistoryArchiveCommand.class))).thenReturn(archived);
        when(fixture.advancer.advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("manager"), eq(null), eq(null), eq(null)))
                .thenReturn(new RuntimeAdvanceResult());

        TaskActionResult result = fixture.coordinator.withdraw(request);
        assertNotNull(result);
        verify(fixture.tasks).cancel("task-1", 3L);
        verify(fixture.advancer).advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("manager"), eq(null), eq(null), eq(null));
    }

    @Test
    void directSendFailsWhenNoVersionedRejectSourceContainsCurrentTask() {
        Fixture fixture = fixture(ActionTypeEnum.DIRECT_SEND);
        DirectSendRequest request = taskRequest(new DirectSendRequest(), "op-direct", fixture.task, fixture.operator);
        request.setTargetNodeCode("manager");
        when(fixture.histories.findLatestByInstanceAndActions(eq("instance-1"), eq(ActionTypeEnum.REJECT.name())))
                .thenReturn(java.util.Collections.<ProcessHistoryTaskEntity>emptyList());

        RuntimeStateException error = assertThrows(RuntimeStateException.class,
                () -> fixture.coordinator.directSend(request));
        assertEquals(RuntimeErrorCodes.DIRECT_SEND_SOURCE_NOT_FOUND, error.getErrorCode());
        verify(fixture.tasks, never()).complete(any(String.class), any(Long.class));
    }

    @Test
    void directSendRejectsForgedTargetAndGroupedContextIsUnavailable() {
        Fixture fixture = fixture(ActionTypeEnum.DIRECT_SEND);
        DirectSendRequest request = taskRequest(new DirectSendRequest(), "op-direct-forged",
                fixture.task, fixture.operator);
        request.setTargetNodeCode("forged");
        fixture.definition.setNodes(java.util.Arrays.asList(userNode("manager",
                "{\"taskActionRules\":{\"directSend\":{\"enabled\":true,\"targetMode\":\"REJECT_SOURCE\"}}}"),
                userNode("finance", null)));
        when(fixture.histories.findLatestByInstanceAndActions("instance-1", ActionTypeEnum.REJECT.name()))
                .thenReturn(java.util.Collections.singletonList(history("reject-source", "finance-user",
                        "finance", ActionTypeEnum.REJECT.name(),
                        "{\"schemaVersion\":1,\"sourceNodeCode\":\"finance\",\"createdTaskIds\":[\"task-1\"]}")));

        RuntimeValidationException error = assertThrows(RuntimeValidationException.class,
                () -> fixture.coordinator.directSend(request));
        assertEquals(RuntimeErrorCodes.DIRECT_SEND_SOURCE_NOT_FOUND, error.getErrorCode());
        verify(fixture.tasks, never()).complete(any(String.class), any(Long.class));

        fixture.task.setTaskGroupId("group-1");
        assertEquals(false, fixture.coordinator.getDirectSendContext(fixture.task, fixture.definition).isAllowed());
    }

    @Test
    void directSendContextIsUnavailableWhenRuleIsClosedOrTaskAlreadyConsumed() {
        Fixture fixture = fixture(ActionTypeEnum.DIRECT_SEND);
        fixture.definition.setNodes(java.util.Arrays.asList(userNode("manager", null), userNode("finance", null)));
        assertEquals(false, fixture.coordinator.getDirectSendContext(fixture.task, fixture.definition).isAllowed());

        fixture.definition.setNodes(java.util.Arrays.asList(userNode("manager",
                "{\"taskActionRules\":{\"directSend\":{\"enabled\":true,\"targetMode\":\"REJECT_SOURCE\"}}}"),
                userNode("finance", null)));
        fixture.task.setTaskStatus("COMPLETED");
        assertEquals(false, fixture.coordinator.getDirectSendContext(fixture.task, fixture.definition).isAllowed());
    }

    @Test
    void directSendConsumesRejectSourceAndAdvancesBackToSourceNode() {
        Fixture fixture = fixture(ActionTypeEnum.DIRECT_SEND);
        DirectSendRequest request = taskRequest(new DirectSendRequest(), "op-direct-success", fixture.task, fixture.operator);
        request.setTargetNodeCode("manager");
        fixture.definition.setNodes(java.util.Collections.singletonList(userNode("manager",
                "{\"taskActionRules\":{\"directSend\":{\"enabled\":true,\"targetMode\":\"REJECT_SOURCE\"}}}")));
        ProcessHistoryTaskEntity source = history("reject-source", "user-a", "manager", ActionTypeEnum.REJECT.name(),
                "{\"schemaVersion\":1,\"sourceNodeCode\":\"manager\",\"createdTaskIds\":[\"task-1\"]}");
        ProcessHistoryTaskEntity archived = history("direct-history", "user-a", "manager", ActionTypeEnum.DIRECT_SEND.name(), "{}");
        when(fixture.histories.findLatestByInstanceAndActions(eq("instance-1"), eq(ActionTypeEnum.REJECT.name())))
                .thenReturn(java.util.Collections.singletonList(source));
        when(fixture.tasks.complete("task-1", 3L)).thenReturn(1);
        when(fixture.historyWriter.archive(any(HistoryArchiveCommand.class))).thenReturn(archived);
        when(fixture.advancer.advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("manager"), eq(null), eq(null), eq(null)))
                .thenReturn(new RuntimeAdvanceResult());

        TaskActionResult result = fixture.coordinator.directSend(request);
        assertNotNull(result);
        verify(fixture.tasks).complete("task-1", 3L);
        verify(fixture.advancer).advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("manager"), eq(null), eq(null), eq(null));
    }

    @Test
    void trustedDirectSendContextExposesOnlyRejectSourceTarget() {
        Fixture fixture = fixture(ActionTypeEnum.DIRECT_SEND);
        ProcessNodeDTO current = userNode("manager",
                "{\"taskActionRules\":{\"directSend\":{\"enabled\":true,\"targetMode\":\"REJECT_SOURCE\"}}}");
        ProcessNodeDTO finance = userNode("finance", null);
        finance.setNodeName("财务审批");
        fixture.definition.setNodes(java.util.Arrays.asList(current, finance));
        when(fixture.histories.findLatestByInstanceAndActions("instance-1", ActionTypeEnum.REJECT.name()))
                .thenReturn(java.util.Collections.singletonList(history("reject-source", "finance-user",
                        "finance", ActionTypeEnum.REJECT.name(),
                        "{\"schemaVersion\":1,\"sourceNodeCode\":\"finance\",\"createdTaskIds\":[\"task-1\"]}")));

        DirectSendContextDTO context = fixture.coordinator.getDirectSendContext(fixture.task, fixture.definition);

        assertEquals(true, context.isAllowed());
        assertEquals("finance", context.getTargetNodeCode());
        assertEquals("财务审批", context.getTargetNodeName());
    }

    @Test
    void starterDirectSendUpdatesVariablesAndChecksAttachmentsBeforeCompletingTask() {
        Fixture fixture = fixture(ActionTypeEnum.DIRECT_SEND);
        fixture.task.setNodeCode("apply");
        fixture.instance.setVariablesJson("{\"amount\":100}");
        DirectSendRequest request = taskRequest(new DirectSendRequest(), "op-direct-starter",
                fixture.task, fixture.operator);
        request.setTargetNodeCode("manager");
        java.util.Map<String, Object> variables = new LinkedHashMap<String, Object>();
        variables.put("amount", Integer.valueOf(250));
        request.setVariables(variables);
        fixture.definition.setNodes(java.util.Arrays.asList(
                userNode("apply", ApproverRuleTypeEnum.STARTER,
                        "{\"taskActionRules\":{\"directSend\":{\"enabled\":true,\"targetMode\":\"REJECT_SOURCE\"}}}"),
                userNode("manager", null)));
        when(fixture.histories.findLatestByInstanceAndActions("instance-1", ActionTypeEnum.REJECT.name()))
                .thenReturn(java.util.Collections.singletonList(history("reject-source", "manager-user",
                        "manager", ActionTypeEnum.REJECT.name(),
                        "{\"schemaVersion\":1,\"sourceNodeCode\":\"manager\",\"createdTaskIds\":[\"task-1\"]}")));
        when(fixture.instances.updateVariablesJson(eq("instance-1"), any(String.class))).thenReturn(1);
        when(fixture.tasks.complete("task-1", 3L)).thenReturn(1);
        when(fixture.historyWriter.archive(any(HistoryArchiveCommand.class))).thenReturn(
                history("direct-history", "user-a", "apply", ActionTypeEnum.DIRECT_SEND.name(), "{}"));
        when(fixture.advancer.advanceToNode(eq(fixture.instance), eq(fixture.definition), eq("manager"),
                eq(null), eq(null), eq(null))).thenReturn(new RuntimeAdvanceResult());
        AttachmentService attachments = mock(AttachmentService.class);
        AttachmentTemplateCheckResult passed = new AttachmentTemplateCheckResult(); passed.setPassed(true);
        when(attachments.checkRequiredAttachments(any())).thenReturn(passed);
        fixture.coordinator.setAttachmentService(attachments);

        fixture.coordinator.directSend(request);

        verify(fixture.instances).updateVariablesJson(eq("instance-1"),
                org.mockito.ArgumentMatchers.contains("\"amount\":250"));
        verify(attachments).checkRequiredAttachments(any());
        verify(fixture.tasks).complete("task-1", 3L);
    }

    @Test
    void nonStarterDirectSendRejectsVariableMutationBeforeTaskCompletion() {
        Fixture fixture = fixture(ActionTypeEnum.DIRECT_SEND);
        DirectSendRequest request = taskRequest(new DirectSendRequest(), "op-direct-user",
                fixture.task, fixture.operator);
        request.setTargetNodeCode("finance");
        request.setVariables(java.util.Collections.<String, Object>singletonMap("amount", Integer.valueOf(250)));
        fixture.definition.setNodes(java.util.Arrays.asList(userNode("manager",
                "{\"taskActionRules\":{\"directSend\":{\"enabled\":true,\"targetMode\":\"REJECT_SOURCE\"}}}"),
                userNode("finance", null)));
        when(fixture.histories.findLatestByInstanceAndActions("instance-1", ActionTypeEnum.REJECT.name()))
                .thenReturn(java.util.Collections.singletonList(history("reject-source", "finance-user",
                        "finance", ActionTypeEnum.REJECT.name(),
                        "{\"schemaVersion\":1,\"sourceNodeCode\":\"finance\",\"createdTaskIds\":[\"task-1\"]}")));

        RuntimeValidationException error = assertThrows(RuntimeValidationException.class,
                () -> fixture.coordinator.directSend(request));

        assertEquals(RuntimeErrorCodes.INVALID_ACTION, error.getErrorCode());
        verify(fixture.instances, never()).updateVariablesJson(any(String.class), any(String.class));
        verify(fixture.tasks, never()).complete(any(String.class), any(Long.class));
    }

    @Test
    void addSignRejectsDuplicateOrOperatorUserBeforeCancellingSourceTask() {
        Fixture fixture = fixture(ActionTypeEnum.ADD_SIGN);
        AddSignRequest request = taskRequest(new AddSignRequest(), "op-add-sign", fixture.task, fixture.operator);
        request.setAddSignUserIds(java.util.Arrays.asList("user-a", "user-b"));

        RuntimeValidationException error = assertThrows(RuntimeValidationException.class,
                () -> fixture.coordinator.addSign(request));
        assertEquals(RuntimeErrorCodes.INVALID_ACTION, error.getErrorCode());
        verify(fixture.tasks, never()).cancel(any(String.class), any(Long.class));
    }

    @Test
    void addSignCancelsSourceCreatesCounterSignGroupAndTemporaryTask() {
        Fixture fixture = fixture(ActionTypeEnum.ADD_SIGN);
        AddSignRequest request = taskRequest(new AddSignRequest(), "op-add-sign-success", fixture.task, fixture.operator);
        request.setAddSignUserIds(java.util.Collections.singletonList("user-b"));
        when(fixture.organization.findUser("user-b")).thenReturn(Optional.of(new UserDTO("user-b", "User B")));
        when(fixture.tasks.cancel("task-1", 3L)).thenReturn(1);
        when(fixture.groups.insert(any(ProcessTaskGroupEntity.class))).thenReturn(1);
        when(fixture.tasks.insert(any(ProcessActiveTaskEntity.class))).thenReturn(1);
        when(fixture.historyWriter.archive(any(HistoryArchiveCommand.class)))
                .thenReturn(history("add-sign-history", "user-a", "manager", ActionTypeEnum.ADD_SIGN.name(), "{}"));

        TaskActionResult result = fixture.coordinator.addSign(request);
        assertNotNull(result);
        assertEquals(1, result.getCreatedTasks().size());
        verify(fixture.tasks).cancel("task-1", 3L);
        verify(fixture.groups).insert(any(ProcessTaskGroupEntity.class));
        verify(fixture.tasks).insert(any(ProcessActiveTaskEntity.class));
    }

    @Test
    void transferUpdatesAssigneeRecordsHistoryAndReturnsNewTaskVersion() {
        ProcessInstanceRepository instances = mock(ProcessInstanceRepository.class);
        ActiveTaskRepository tasks = mock(ActiveTaskRepository.class);
        ProcessHistoryTaskRepository histories = mock(ProcessHistoryTaskRepository.class);
        TaskGroupRepository groups = mock(TaskGroupRepository.class);
        RuntimeDefinitionLoader definitions = mock(RuntimeDefinitionLoader.class);
        RuntimeRequestValidator validator = mock(RuntimeRequestValidator.class);
        RuntimeOperationExecutor operations = mock(RuntimeOperationExecutor.class);
        RuntimeNodeAdvancer advancer = mock(RuntimeNodeAdvancer.class);
        RuntimeStateValidator state = mock(RuntimeStateValidator.class);
        HistoryTaskWriter writer = mock(HistoryTaskWriter.class);
        CallbackService callbacks = mock(CallbackService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        OrganizationProvider organization = mock(OrganizationProvider.class);
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("organizationProvider", organization);

        EnhancedTaskActionCoordinator coordinator = new EnhancedTaskActionCoordinator(instances, tasks, histories,
                groups, definitions, validator, operations, advancer, state, writer, null, callbacks,
                factory.getBeanProvider(OrganizationProvider.class),
                auditLogWriter);
        TransferTaskRequest request = new TransferTaskRequest();
        request.setOperationId("op-transfer");
        request.setTaskId("task-1");
        request.setExpectedTaskVersion(Long.valueOf(3));
        request.setOperatorUserId("user-a");
        request.setTargetUserId("user-b");
        UserContext operator = new UserContext("user-a", "User A", null, null);
        ProcessInstanceEntity instance = instance();
        ProcessActiveTaskEntity task = task();
        ProcessHistoryTaskEntity archived = new ProcessHistoryTaskEntity();
        archived.setId("history-1");
        archived.setActiveTaskId(task.getId());
        archived.setInstanceId(instance.getId());

        when(validator.validateTaskIdentity(request)).thenReturn(operator);
        when(operations.begin(eq(request), eq(RuntimeOperationTypes.TRANSFER), eq("user-a"), eq(null),
                eq("task-1"), any(LocalDateTime.class)))
                .thenReturn(new OperationIdempotencyDecision(OperationIdempotencyDecisionType.NEW, null));
        when(state.validateTaskAction("task-1", Long.valueOf(3), ActionTypeEnum.TRANSFER, operator))
                .thenReturn(new RuntimeTaskContext(instance, task, ActionTypeEnum.TRANSFER, operator));
        when(definitions.loadForInstance(instance)).thenReturn(new ProcessDefinitionDetailDTO());
        when(organization.findUser("user-b")).thenReturn(Optional.of(new UserDTO("user-b", "User B")));
        when(tasks.transfer("task-1", 3L, "user-b", "User B")).thenReturn(1);
        when(writer.archive(any())).thenReturn(archived);
        when(instances.findById("instance-1")).thenReturn(instance);

        com.flowmind.platform.api.dto.TaskActionResult result = coordinator.transfer(request);
        assertEquals("user-b", result.getUpdatedTasks().get(0).getAssigneeUserId());
        assertEquals(Long.valueOf(4), result.getUpdatedTasks().get(0).getTaskVersion());
        verify(tasks).transfer("task-1", 3L, "user-b", "User B");
        org.mockito.ArgumentCaptor<AuditLogCommand> audit =
                org.mockito.ArgumentCaptor.forClass(AuditLogCommand.class);
        verify(auditLogWriter).append(audit.capture());
        assertEquals("instance-1", audit.getValue().getInstanceId());
        assertEquals("op-transfer", audit.getValue().getOperationId());
        assertEquals(com.flowmind.platform.api.enums.OperationTargetTypeEnum.TASK,
                audit.getValue().getTargetType());
        assertEquals("task-1", audit.getValue().getTargetId());
        assertEquals(ActionTypeEnum.TRANSFER.name(), audit.getValue().getActionType());
        assertEquals("user-a", audit.getValue().getOperatorId());
        assertEquals(java.util.Collections.singletonList("history-1"),
                audit.getValue().getDetail().get("historyTaskIds"));
        verify(operations, never()).markDeterministicFailure(eq("op-transfer"), any(String.class));
    }

    @Test
    void delegateTaskUpdatesAssigneeAndMarksDelegateSourceWithoutOrganizationSpi() {
        Fixture fixture = fixture(ActionTypeEnum.TRANSFER);
        DelegateTaskRequest request = taskRequest(new DelegateTaskRequest(), "op-delegate",
                fixture.task, fixture.operator);
        request.setTargetUserId("user-b");
        request.setTargetUserName("User B");
        ProcessHistoryTaskEntity archived = history("delegate-history", "user-a", "manager",
                ActionTypeEnum.TRANSFER.name(), "{}");

        when(fixture.tasks.delegateTask("task-1", 3L, "user-b", "User B", "user-a", "User A")).thenReturn(1);
        when(fixture.historyWriter.archive(any())).thenReturn(archived);

        TaskActionResult result = fixture.coordinator.delegateTask(request);

        assertEquals("user-b", result.getUpdatedTasks().get(0).getAssigneeUserId());
        assertEquals("User B", result.getUpdatedTasks().get(0).getAssigneeUserName());
        assertEquals("user-a", result.getUpdatedTasks().get(0).getDelegateFromUserId());
        assertEquals("User A", result.getUpdatedTasks().get(0).getDelegateFromUserName());
        verify(fixture.tasks).delegateTask("task-1", 3L, "user-b", "User B", "user-a", "User A");
        verify(fixture.organization, never()).findUser(any(String.class));
        org.mockito.ArgumentCaptor<HistoryArchiveCommand> archive =
                org.mockito.ArgumentCaptor.forClass(HistoryArchiveCommand.class);
        verify(fixture.historyWriter).archive(archive.capture());
        assertEquals("user-a", archive.getValue().getTask().getDelegateFromUserId());
        assertEquals("User A", archive.getValue().getTask().getDelegateFromUserName());
    }

    private <T extends com.flowmind.platform.api.request.TaskOperationRequest> T taskRequest(T request,
                                                                                              String operationId,
                                                                                              ProcessActiveTaskEntity task,
                                                                                              UserContext operator) {
        request.setOperationId(operationId);
        request.setTaskId(task.getId());
        request.setExpectedTaskVersion(task.getLockVersion());
        request.setOperatorUserId(operator.getUserId());
        return request;
    }

    private void allowReachable(Fixture fixture, String nodeCode) {
        when(fixture.advancer.reachableUserTaskNodeCodes(fixture.instance, fixture.definition))
                .thenReturn(new java.util.LinkedHashSet<String>(java.util.Collections.singletonList(nodeCode)));
    }

    private Fixture fixture(ActionTypeEnum action) {
        ProcessInstanceRepository instances = mock(ProcessInstanceRepository.class);
        ActiveTaskRepository tasks = mock(ActiveTaskRepository.class);
        ProcessHistoryTaskRepository histories = mock(ProcessHistoryTaskRepository.class);
        TaskGroupRepository groups = mock(TaskGroupRepository.class);
        RuntimeDefinitionLoader definitions = mock(RuntimeDefinitionLoader.class);
        RuntimeRequestValidator validator = mock(RuntimeRequestValidator.class);
        RuntimeOperationExecutor operations = mock(RuntimeOperationExecutor.class);
        RuntimeNodeAdvancer advancer = mock(RuntimeNodeAdvancer.class);
        RuntimeStateValidator state = mock(RuntimeStateValidator.class);
        HistoryTaskWriter writer = mock(HistoryTaskWriter.class);
        CallbackService callbacks = mock(CallbackService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        OrganizationProvider organization = mock(OrganizationProvider.class);
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("organizationProvider", organization);
        EnhancedTaskActionCoordinator coordinator = new EnhancedTaskActionCoordinator(instances, tasks, histories,
                groups, definitions, validator, operations, advancer, state, writer, null, callbacks,
                factory.getBeanProvider(OrganizationProvider.class),
                auditLogWriter);
        UserContext operator = new UserContext("user-a", "User A", null, null);
        ProcessInstanceEntity instance = instance();
        ProcessActiveTaskEntity task = task();
        when(validator.validateTaskIdentity(any(com.flowmind.platform.api.request.TaskOperationRequest.class))).thenReturn(operator);
        when(operations.begin(any(com.flowmind.platform.api.request.OperationRequest.class), any(String.class), any(String.class),
                any(String.class), any(String.class), any(LocalDateTime.class)))
                .thenReturn(new OperationIdempotencyDecision(OperationIdempotencyDecisionType.NEW, null));
        when(state.validateTaskAction(eq(task.getId()), eq(task.getLockVersion()), eq(action), eq(operator)))
                .thenReturn(new RuntimeTaskContext(instance, task, action, operator));
        when(definitions.loadForInstance(instance)).thenReturn(new ProcessDefinitionDetailDTO());
        when(instances.findById(instance.getId())).thenReturn(instance);
        ProcessDefinitionDetailDTO definition = new ProcessDefinitionDetailDTO();
        return new Fixture(coordinator, instances, tasks, histories, groups, operator, instance, task,
                definitions, definition, advancer, writer, organization);
    }

    private ProcessNodeDTO userNode(String code, String listenerConfig) {
        return userNode(code, ApproverRuleTypeEnum.USER, listenerConfig);
    }

    private ProcessNodeDTO userNode(String code, ApproverRuleTypeEnum approverRuleType, String listenerConfig) {
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setNodeCode(code);
        node.setNodeType(NodeTypeEnum.USER_TASK);
        node.setApproverRuleType(approverRuleType);
        node.setListenerConfig(listenerConfig);
        return node;
    }

    private ProcessNodeDTO gatewayNode(String code, NodeTypeEnum type, String pairedGatewayCode) {
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setNodeCode(code);
        node.setNodeType(type);
        node.setPairedGatewayCode(pairedGatewayCode);
        return node;
    }

    private ProcessEdgeDTO edge(String code, String source, String target) {
        ProcessEdgeDTO edge = new ProcessEdgeDTO();
        edge.setEdgeCode(code);
        edge.setSourceNodeCode(source);
        edge.setTargetNodeCode(target);
        return edge;
    }

    private ProcessTaskGroupEntity parallelGroup(String id) {
        ProcessTaskGroupEntity group = new ProcessTaskGroupEntity();
        group.setId(id);
        group.setInstanceId("instance-1");
        group.setNodeCode("split");
        group.setJoinNodeCode("join");
        group.setGroupType("PARALLEL_GATEWAY");
        group.setGroupStatus("ACTIVE");
        group.setLockVersion(Long.valueOf(2));
        group.setBranchStateJson("{}");
        return group;
    }

    private ProcessHistoryTaskEntity history(String id, String assignee, String nodeCode, String action, String extraJson) {
        ProcessHistoryTaskEntity history = new ProcessHistoryTaskEntity();
        history.setId(id);
        history.setInstanceId("instance-1");
        history.setActiveTaskId("history-task-" + id);
        history.setAssigneeUserId(assignee);
        history.setNodeCode(nodeCode);
        history.setActionType(action);
        history.setExtraJson(extraJson);
        return history;
    }

    private static final class Fixture {
        private final EnhancedTaskActionCoordinator coordinator;
        private final ProcessInstanceRepository instances;
        private final ActiveTaskRepository tasks;
        private final ProcessHistoryTaskRepository histories;
        private final TaskGroupRepository groups;
        private final UserContext operator;
        private final ProcessInstanceEntity instance;
        private final ProcessActiveTaskEntity task;
        private final RuntimeDefinitionLoader definitions;
        private final ProcessDefinitionDetailDTO definition;
        private final RuntimeNodeAdvancer advancer;
        private final HistoryTaskWriter historyWriter;
        private final OrganizationProvider organization;

        private Fixture(EnhancedTaskActionCoordinator coordinator, ProcessInstanceRepository instances,
                        ActiveTaskRepository tasks, ProcessHistoryTaskRepository histories, TaskGroupRepository groups,
                        UserContext operator, ProcessInstanceEntity instance, ProcessActiveTaskEntity task,
                        RuntimeDefinitionLoader definitions, ProcessDefinitionDetailDTO definition,
                        RuntimeNodeAdvancer advancer, HistoryTaskWriter historyWriter,
                        OrganizationProvider organization) {
            this.coordinator = coordinator;
            this.instances = instances;
            this.tasks = tasks;
            this.histories = histories;
            this.groups = groups;
            this.operator = operator;
            this.instance = instance;
            this.task = task;
            this.definitions = definitions;
            this.definition = definition;
            this.advancer = advancer;
            this.historyWriter = historyWriter;
            this.organization = organization;
            when(definitions.loadForInstance(instance)).thenReturn(definition);
        }
    }

    private ProcessInstanceEntity instance() {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        instance.setId("instance-1"); instance.setDefinitionId("definition-1"); instance.setProcessCode("leave");
        instance.setProcessName("Leave"); instance.setInstanceStatus("RUNNING"); instance.setVariablesJson("{}");
        return instance;
    }

    private ProcessActiveTaskEntity task() {
        ProcessActiveTaskEntity task = new ProcessActiveTaskEntity();
        task.setId("task-1"); task.setInstanceId("instance-1"); task.setDefinitionId("definition-1");
        task.setNodeCode("manager"); task.setAssigneeUserId("user-a"); task.setAssigneeUserName("User A");
        task.setTaskStatus("ACTIVE"); task.setLockVersion(Long.valueOf(3)); task.setCreatedAt(LocalDateTime.now());
        return task;
    }
}

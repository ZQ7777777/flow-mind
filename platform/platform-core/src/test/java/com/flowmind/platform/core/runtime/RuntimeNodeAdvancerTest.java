package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.DepartmentDTO;
import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.api.enums.TaskGroupTypeEnum;
import com.flowmind.platform.api.request.ApproverResolveRequest;
import com.flowmind.platform.api.spi.OrganizationProvider;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.entity.ProcessTaskGroupEntity;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import com.flowmind.platform.persistence.repository.TaskGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeNodeAdvancerTest {

    private ActiveTaskRepository activeTaskRepository;
    private TaskGroupRepository taskGroupRepository;
    private ProcessInstanceRepository instanceRepository;
    private RuntimeRequestValidator requestValidator;
    private com.flowmind.platform.api.spi.ApproverResolver approverResolver;
    private com.flowmind.platform.api.spi.ConditionExpressionEvaluator conditionEvaluator;
    private ApproverResolveRequestFactory approverResolveRequestFactory;
    private RuntimeNodeAdvancer advancer;

    @BeforeEach
    void setUp() {
        activeTaskRepository = mock(ActiveTaskRepository.class);
        taskGroupRepository = mock(TaskGroupRepository.class);
        instanceRepository = mock(ProcessInstanceRepository.class);
        requestValidator = new RuntimeRequestValidator(() -> null);
        approverResolver = mock(com.flowmind.platform.api.spi.ApproverResolver.class);
        conditionEvaluator = mock(com.flowmind.platform.api.spi.ConditionExpressionEvaluator.class);
        approverResolveRequestFactory = new ApproverResolveRequestFactory(new RuntimeNodeConfigReader());
        advancer = new RuntimeNodeAdvancer(activeTaskRepository, taskGroupRepository, instanceRepository,
                requestValidator, approverResolver, conditionEvaluator, approverResolveRequestFactory);
        when(activeTaskRepository.insert(any(ProcessActiveTaskEntity.class))).thenReturn(1);
        when(activeTaskRepository.findOpenByInstanceId("instance-1"))
                .thenReturn(Collections.<ProcessActiveTaskEntity>emptyList());
        when(instanceRepository.updateCurrentNodeCodes(eq("instance-1"), anyString())).thenReturn(1);
    }

    @Test
    void createsSingleCandidateTaskWithResolvedApproversAndRefreshesCurrentNodes() {
        when(approverResolver.resolveApprovers(any())).thenReturn(Arrays.asList(
                new UserDTO("manager-1", "Mia"), new UserDTO("manager-2", "Noah")));
        ProcessActiveTaskEntity persistedTask = new ProcessActiveTaskEntity();
        persistedTask.setNodeCode("review");
        when(activeTaskRepository.findOpenByInstanceId("instance-1"))
                .thenReturn(Collections.singletonList(persistedTask));

        RuntimeAdvanceResult result = advancer.advanceToNode(instance(), definition(
                nodes(userTask("review")), edges()), "review", null, null);

        ArgumentCaptor<ProcessActiveTaskEntity> taskCaptor = ArgumentCaptor.forClass(ProcessActiveTaskEntity.class);
        verify(activeTaskRepository).insert(taskCaptor.capture());
        ProcessActiveTaskEntity task = taskCaptor.getValue();
        assertEquals("instance-1", task.getInstanceId());
        assertEquals("definition-1", task.getDefinitionId());
        assertEquals("review", task.getNodeCode());
        assertEquals("[\"manager-1\",\"manager-2\"]", task.getCandidateUserIds());
        assertEquals(1, result.getCreatedTasks().size());
        assertEquals("Review", result.getCreatedTasks().get(0).getNodeName());
        ArgumentCaptor<ApproverResolveRequest> requestCaptor = ArgumentCaptor.forClass(ApproverResolveRequest.class);
        verify(approverResolver).resolveApprovers(requestCaptor.capture());
        assertEquals(Collections.singletonList("user-1"), requestCaptor.getValue()
                .getApproverRuleConfig().get("userIds"));
        verify(instanceRepository).updateCurrentNodeCodes("instance-1", "[\"review\"]");
    }

    @Test
    void createsStarterNoticeWithoutTaskAndAutomaticallyCompletesInstance() {
        when(approverResolver.resolveApprovers(any())).thenReturn(
                Collections.singletonList(new UserDTO("starter-1", "Starter")));
        when(instanceRepository.complete(eq("instance-1"), any())).thenReturn(1);
        ProcessNodeDTO notice = node("notify-starter", NodeTypeEnum.NOTICE);
        notice.setNodeName("知会经办");
        notice.setApproverRuleType(ApproverRuleTypeEnum.STARTER);
        notice.setMultiInstanceMode(MultiInstanceModeEnum.SINGLE);
        notice.setNoticeConfig("{\"title\":\"办理完成\"}");
        ProcessInstanceEntity instance = instance();
        instance.setInstanceTitle("仓单质押申请-001");

        RuntimeAdvanceResult result = advancer.advanceToNode(instance, definition(
                nodes(notice, node("end", NodeTypeEnum.END)),
                edges(edge("e1", "notify-starter", "end"))), "notify-starter", null, null);

        assertTrue(result.isInstanceCompleted());
        assertTrue(result.getCreatedTasks().isEmpty());
        assertEquals(1, result.getCreatedNotices().size());
        assertEquals("办理完成", result.getCreatedNotices().get(0).getTitle());
        assertEquals("流程「仓单质押申请-001」已办理完成，请知悉",
                result.getCreatedNotices().get(0).getContent());
        assertEquals(Collections.singletonList("starter-1"),
                result.getCreatedNotices().get(0).getTargetUserIds());
        verify(activeTaskRepository, never()).insert(any(ProcessActiveTaskEntity.class));
        verify(instanceRepository).complete(eq("instance-1"), any());
    }

    @Test
    void createsTaskOnlyWithDistinctValidResolvedApprovers() {
        when(approverResolver.resolveApprovers(any())).thenReturn(Arrays.asList(
                new UserDTO("manager-1", "Mia"), new UserDTO(null, "Missing"),
                new UserDTO("manager-1", "Duplicate"), new UserDTO("manager-2", "Noah")));

        advancer.advanceToNode(instance(), definition(nodes(userTask("review")), edges()), "review", null, null);

        ArgumentCaptor<ProcessActiveTaskEntity> taskCaptor = ArgumentCaptor.forClass(ProcessActiveTaskEntity.class);
        verify(activeTaskRepository).insert(taskCaptor.capture());
        assertEquals("[\"manager-1\",\"manager-2\"]", taskCaptor.getValue().getCandidateUserIds());
    }

    @Test
    void createsTaskGroupAndOneTaskPerApproverForOrSignNode() {
        when(taskGroupRepository.insert(any(ProcessTaskGroupEntity.class))).thenReturn(1);
        when(approverResolver.resolveApprovers(any())).thenReturn(Arrays.asList(
                new UserDTO("manager-2", "Noah"), new UserDTO("manager-1", "Mia")));
        ProcessNodeDTO review = userTask("review");
        review.setMultiInstanceMode(MultiInstanceModeEnum.OR_SIGN);

        RuntimeAdvanceResult result = advancer.advanceToNode(instance(), definition(nodes(review), edges()),
                "review", "parallel-group", "branch-a");

        ArgumentCaptor<ProcessTaskGroupEntity> groupCaptor = ArgumentCaptor.forClass(ProcessTaskGroupEntity.class);
        verify(taskGroupRepository).insert(groupCaptor.capture());
        ProcessTaskGroupEntity group = groupCaptor.getValue();
        assertEquals("instance-1", group.getInstanceId());
        assertEquals("review", group.getNodeCode());
        assertEquals(TaskGroupTypeEnum.OR_SIGN.name(), group.getGroupType());
        assertEquals(Integer.valueOf(2), group.getTotalCount());
        assertEquals(Integer.valueOf(0), group.getCompletedCount());
        assertEquals("ACTIVE", group.getGroupStatus());
        assertEquals(Long.valueOf(0L), group.getLockVersion());
        assertEquals("parallel-group", group.getParentGroupId());
        assertEquals("branch-a", group.getParentBranchKey());
        ArgumentCaptor<ProcessActiveTaskEntity> taskCaptor = ArgumentCaptor.forClass(ProcessActiveTaskEntity.class);
        verify(activeTaskRepository, org.mockito.Mockito.times(2)).insert(taskCaptor.capture());
        List<ProcessActiveTaskEntity> tasks = taskCaptor.getAllValues();
        assertEquals(group.getId(), tasks.get(0).getTaskGroupId());
        assertEquals("branch-a", tasks.get(0).getBranchKey());
        assertEquals("[\"manager-1\"]", tasks.get(0).getCandidateUserIds());
        assertEquals(group.getId(), tasks.get(1).getTaskGroupId());
        assertEquals("branch-a", tasks.get(1).getBranchKey());
        assertEquals("[\"manager-2\"]", tasks.get(1).getCandidateUserIds());
        assertEquals(2, result.getCreatedTasks().size());
        assertEquals(Collections.singletonList("manager-1"), result.getCreatedTasks().get(0).getCandidateUserIds());
        assertEquals(Collections.singletonList("manager-2"), result.getCreatedTasks().get(1).getCandidateUserIds());
        ArgumentCaptor<ApproverResolveRequest> requestCaptor = ArgumentCaptor.forClass(ApproverResolveRequest.class);
        verify(approverResolver).resolveApprovers(requestCaptor.capture());
        assertEquals(MultiInstanceModeEnum.OR_SIGN, requestCaptor.getValue().getMultiInstanceMode());
    }

    @Test
    void createsCountersignGroupAndOneTaskPerResolvedApproverWithParentContext() {
        when(approverResolver.resolveApprovers(any())).thenReturn(Arrays.asList(
                new UserDTO("manager-2", "Noah"), new UserDTO("manager-1", "Mia")));
        when(taskGroupRepository.insert(any(ProcessTaskGroupEntity.class))).thenReturn(1);
        ProcessNodeDTO review = userTask("review");
        review.setMultiInstanceMode(MultiInstanceModeEnum.COUNTERSIGN);

        RuntimeAdvanceResult result = advancer.advanceToNode(instance(), definition(nodes(review), edges()),
                "review", "parallel-1", "branch-a");

        ArgumentCaptor<ProcessTaskGroupEntity> groupCaptor = ArgumentCaptor.forClass(ProcessTaskGroupEntity.class);
        verify(taskGroupRepository).insert(groupCaptor.capture());
        ProcessTaskGroupEntity group = groupCaptor.getValue();
        assertEquals("COUNTERSIGN", group.getGroupType());
        assertEquals(Integer.valueOf(2), group.getTotalCount());
        assertEquals("parallel-1", group.getParentGroupId());
        assertEquals("branch-a", group.getParentBranchKey());

        ArgumentCaptor<ProcessActiveTaskEntity> taskCaptor = ArgumentCaptor.forClass(ProcessActiveTaskEntity.class);
        verify(activeTaskRepository, org.mockito.Mockito.times(2)).insert(taskCaptor.capture());
        assertEquals("[\"manager-1\"]", taskCaptor.getAllValues().get(0).getCandidateUserIds());
        assertEquals("[\"manager-2\"]", taskCaptor.getAllValues().get(1).getCandidateUserIds());
        assertEquals(group.getId(), taskCaptor.getAllValues().get(0).getTaskGroupId());
        assertEquals("branch-a", taskCaptor.getAllValues().get(0).getBranchKey());
        assertEquals(2, result.getCreatedTasks().size());
    }

    @Test
    void onePersonCountersignStillCreatesTaskGroup() {
        when(approverResolver.resolveApprovers(any()))
                .thenReturn(Collections.singletonList(new UserDTO("manager-1", "Mia")));
        when(taskGroupRepository.insert(any(ProcessTaskGroupEntity.class))).thenReturn(1);
        ProcessNodeDTO review = userTask("review");
        review.setMultiInstanceMode(MultiInstanceModeEnum.COUNTERSIGN);

        RuntimeAdvanceResult result = advancer.advanceToNode(instance(), definition(nodes(review), edges()),
                "review", null, null);

        ArgumentCaptor<ProcessTaskGroupEntity> groupCaptor = ArgumentCaptor.forClass(ProcessTaskGroupEntity.class);
        verify(taskGroupRepository).insert(groupCaptor.capture());
        assertEquals(Integer.valueOf(1), groupCaptor.getValue().getTotalCount());
        assertEquals(1, result.getCreatedTasks().size());
    }

    @Test
    void countersignTaskInsertFailureStopsCreationWithStableStateError() {
        when(approverResolver.resolveApprovers(any())).thenReturn(Arrays.asList(
                new UserDTO("manager-1", "Mia"), new UserDTO("manager-2", "Noah")));
        when(taskGroupRepository.insert(any(ProcessTaskGroupEntity.class))).thenReturn(1);
        when(activeTaskRepository.insert(any(ProcessActiveTaskEntity.class))).thenReturn(1, 0);
        ProcessNodeDTO review = userTask("review");
        review.setMultiInstanceMode(MultiInstanceModeEnum.COUNTERSIGN);

        RuntimeStateException error = assertThrows(RuntimeStateException.class,
                () -> advancer.advanceToNode(instance(), definition(nodes(review), edges()),
                        "review", null, null));

        assertEquals(RuntimeErrorCodes.INVALID_ACTION, error.getErrorCode());
        verify(activeTaskRepository, org.mockito.Mockito.times(2)).insert(any(ProcessActiveTaskEntity.class));
    }

    @Test
    void createsTaskWithDefaultApproverResolverAndOrganizationProvider() {
        MapBackedOrganizationProvider organizationProvider = new MapBackedOrganizationProvider();
        organizationProvider.addUser("user-1", "User One");
        RuntimeNodeAdvancer defaultResolverAdvancer = new RuntimeNodeAdvancer(activeTaskRepository,
                taskGroupRepository, instanceRepository, requestValidator,
                new DefaultApproverResolver(organizationProvider), conditionEvaluator, approverResolveRequestFactory);

        defaultResolverAdvancer.advanceToNode(instance(), definition(nodes(userTask("review")), edges()),
                "review", null, null);

        ArgumentCaptor<ProcessActiveTaskEntity> taskCaptor = ArgumentCaptor.forClass(ProcessActiveTaskEntity.class);
        verify(activeTaskRepository).insert(taskCaptor.capture());
        assertEquals("[\"user-1\"]", taskCaptor.getValue().getCandidateUserIds());
    }

    @Test
    void exclusiveGatewayUsesFirstMatchingConditionInStableEdgeOrder() {
        ProcessEdgeDTO second = edge("route-b", "route", "second");
        second.setSortOrder(Integer.valueOf(20));
        second.setConditionExpression("second");
        ProcessEdgeDTO first = edge("route-a", "route", "first");
        first.setSortOrder(Integer.valueOf(10));
        first.setConditionExpression("first");
        when(conditionEvaluator.evaluate("first", Collections.<String, Object>emptyMap())).thenReturn(false);
        when(conditionEvaluator.evaluate("second", Collections.<String, Object>emptyMap())).thenReturn(true);
        when(approverResolver.resolveApprovers(any()))
                .thenReturn(Collections.singletonList(new UserDTO("u-1", "User")));

        RuntimeAdvanceResult result = advancer.advanceToNode(instance(), definition(
                nodes(node("route", NodeTypeEnum.EXCLUSIVE_GATEWAY), userTask("first"), userTask("second")),
                edges(first, second)), "route", null, null);

        assertEquals(1, result.getCreatedTasks().size());
        assertEquals("second", result.getCreatedTasks().get(0).getNodeCode());
        verify(conditionEvaluator).evaluate("first", Collections.<String, Object>emptyMap());
        verify(conditionEvaluator).evaluate("second", Collections.<String, Object>emptyMap());
    }

    @Test
    void exclusiveGatewayUsesSimpleSpiEvaluatorWithRuntimeVariables() {
        RuntimeNodeAdvancer productionAdvancer = new RuntimeNodeAdvancer(activeTaskRepository,
                taskGroupRepository, instanceRepository, requestValidator, approverResolver,
                new SimpleConditionExpressionEvaluator(), approverResolveRequestFactory);
        ProcessInstanceEntity runtimeInstance = instance();
        runtimeInstance.setVariablesJson("{\"amount\":120000}");
        ProcessEdgeDTO highAmount = conditionalEdge("high-amount", "route", "high-task", "amount > 100000");
        ProcessEdgeDTO defaultEdge = edge("default", "route", "default-task");
        defaultEdge.setDefaultEdge(Boolean.TRUE);
        when(approverResolver.resolveApprovers(any()))
                .thenReturn(Collections.singletonList(new UserDTO("u-1", "User")));

        RuntimeAdvanceResult result = productionAdvancer.advanceToNode(runtimeInstance, definition(
                nodes(node("route", NodeTypeEnum.EXCLUSIVE_GATEWAY), userTask("high-task"),
                        userTask("default-task")), edges(highAmount, defaultEdge)), "route", null, null);

        assertEquals("high-task", result.getCreatedTasks().get(0).getNodeCode());
    }

    @Test
    void reachableUserTaskNodeCodesFollowsCurrentExclusiveGatewayBranchAndContinuesPastUserTasks() {
        RuntimeNodeAdvancer productionAdvancer = new RuntimeNodeAdvancer(activeTaskRepository,
                taskGroupRepository, instanceRepository, requestValidator, approverResolver,
                new SimpleConditionExpressionEvaluator(), approverResolveRequestFactory);
        ProcessInstanceEntity runtimeInstance = instance();
        runtimeInstance.setVariablesJson("{\"amount\":120000}");
        ProcessEdgeDTO highAmount = conditionalEdge("high-amount", "route", "high-task", "amount > 100000");
        ProcessEdgeDTO defaultEdge = edge("default", "route", "default-task");
        defaultEdge.setDefaultEdge(Boolean.TRUE);

        java.util.Set<String> reachable = productionAdvancer.reachableUserTaskNodeCodes(runtimeInstance, definition(
                nodes(node("start", NodeTypeEnum.START), userTask("apply"),
                        node("route", NodeTypeEnum.EXCLUSIVE_GATEWAY), userTask("high-task"),
                        userTask("default-task"), node("end", NodeTypeEnum.END)),
                edges(edge("start-apply", "start", "apply"), edge("apply-route", "apply", "route"),
                        highAmount, defaultEdge, edge("high-end", "high-task", "end"),
                        edge("default-end", "default-task", "end"))));

        assertTrue(reachable.contains("apply"));
        assertTrue(reachable.contains("high-task"));
        assertFalse(reachable.contains("default-task"));
    }

    @Test
    void exclusiveGatewayFallsBackToDefaultAndRejectsNoMatchOrEvaluatorFailure() {
        ProcessEdgeDTO conditional = edge("conditional", "route", "conditional-task");
        conditional.setConditionExpression("condition");
        ProcessEdgeDTO defaultEdge = edge("default", "route", "default-task");
        defaultEdge.setDefaultEdge(Boolean.TRUE);
        when(conditionEvaluator.evaluate("condition", Collections.<String, Object>emptyMap())).thenReturn(false);
        when(approverResolver.resolveApprovers(any()))
                .thenReturn(Collections.singletonList(new UserDTO("u-1", "User")));

        RuntimeAdvanceResult defaultResult = advancer.advanceToNode(instance(), definition(
                nodes(node("route", NodeTypeEnum.EXCLUSIVE_GATEWAY), userTask("conditional-task"),
                        userTask("default-task")), edges(conditional, defaultEdge)), "route", null, null);
        assertEquals("default-task", defaultResult.getCreatedTasks().get(0).getNodeCode());

        RuntimeStateException noMatch = assertThrows(RuntimeStateException.class,
                () -> advancer.advanceToNode(instance(), definition(
                        nodes(node("route", NodeTypeEnum.EXCLUSIVE_GATEWAY), userTask("conditional-task"),
                                userTask("other-task")),
                        edges(conditional, conditionalEdge("other", "route", "other-task", "other"))),
                        "route", null, null));
        assertEquals(RuntimeErrorCodes.GATEWAY_NO_MATCH, noMatch.getErrorCode());

        when(conditionEvaluator.evaluate("condition", Collections.<String, Object>emptyMap()))
                .thenThrow(new IllegalArgumentException("invalid expression"));
        RuntimeStateException evaluatorFailure = assertThrows(RuntimeStateException.class,
                () -> advancer.advanceToNode(instance(), definition(
                        nodes(node("route", NodeTypeEnum.EXCLUSIVE_GATEWAY), userTask("conditional-task"),
                                userTask("other-task")),
                        edges(conditional, conditionalEdge("other", "route", "other-task", "other"))),
                        "route", null, null));
        assertEquals(RuntimeErrorCodes.GATEWAY_CONFIG_INVALID, evaluatorFailure.getErrorCode());
    }

    @Test
    void detectsAutomaticGatewayCycleBeforeCreatingTask() {
        ProcessEdgeDTO retry = conditionalEdge("retry", "route", "route", "retry");
        ProcessEdgeDTO done = conditionalEdge("done", "route", "done-task", "done");
        when(conditionEvaluator.evaluate("retry", Collections.<String, Object>emptyMap())).thenReturn(true);

        RuntimeStateException error = assertThrows(RuntimeStateException.class,
                () -> advancer.advanceToNode(instance(), definition(
                        nodes(node("route", NodeTypeEnum.EXCLUSIVE_GATEWAY), userTask("done-task")),
                        edges(retry, done)), "route", null, null));

        assertEquals(RuntimeErrorCodes.GATEWAY_CONFIG_INVALID, error.getErrorCode());
    }

    @Test
    void exclusiveGatewayRejectsMissingConditionEvaluatorWithFrozenErrorCode() {
        ProcessEdgeDTO conditional = conditionalEdge("conditional", "route", "conditional-task", "condition");
        ProcessEdgeDTO defaultEdge = edge("default", "route", "default-task");
        defaultEdge.setDefaultEdge(Boolean.TRUE);
        RuntimeNodeAdvancer withoutEvaluator = new RuntimeNodeAdvancer(activeTaskRepository, taskGroupRepository,
                instanceRepository, requestValidator, approverResolver, null, approverResolveRequestFactory);

        RuntimeStateException error = assertThrows(RuntimeStateException.class,
                () -> withoutEvaluator.advanceToNode(instance(), definition(
                        nodes(node("route", NodeTypeEnum.EXCLUSIVE_GATEWAY), userTask("conditional-task"),
                                userTask("default-task")), edges(conditional, defaultEdge)), "route", null, null));

        assertEquals(RuntimeErrorCodes.GATEWAY_CONFIG_INVALID, error.getErrorCode());
    }

    @Test
    void parallelSplitCreatesGroupAndCreatesBranchTasksWithContext() {
        ProcessNodeDTO split = node("split", NodeTypeEnum.PARALLEL_SPLIT_GATEWAY);
        split.setPairedGatewayCode("join");
        ProcessNodeDTO join = node("join", NodeTypeEnum.PARALLEL_JOIN_GATEWAY);
        join.setPairedGatewayCode("split");
        when(taskGroupRepository.insert(any(ProcessTaskGroupEntity.class))).thenReturn(1);
        when(approverResolver.resolveApprovers(any()))
                .thenReturn(Collections.singletonList(new UserDTO("u-1", "User")));

        RuntimeAdvanceResult result = advancer.advanceToNode(instance(), definition(
                nodes(split, join, userTask("branch-a"), userTask("branch-b")),
                edges(edge("branch-a-edge", "split", "branch-a"), edge("branch-b-edge", "split", "branch-b"),
                        edge("a-to-join", "branch-a", "join"), edge("b-to-join", "branch-b", "join"),
                        edge("after-join", "join", "branch-a"))), "split", null, null);

        ArgumentCaptor<ProcessTaskGroupEntity> groupCaptor = ArgumentCaptor.forClass(ProcessTaskGroupEntity.class);
        verify(taskGroupRepository).insert(groupCaptor.capture());
        ProcessTaskGroupEntity group = groupCaptor.getValue();
        assertEquals("PARALLEL_GATEWAY", group.getGroupType());
        assertEquals("join", group.getJoinNodeCode());
        assertEquals("{\"branch-a-edge\":\"RUNNING\",\"branch-b-edge\":\"RUNNING\"}",
                group.getBranchStateJson());
        ArgumentCaptor<ProcessActiveTaskEntity> taskCaptor = ArgumentCaptor.forClass(ProcessActiveTaskEntity.class);
        verify(activeTaskRepository, org.mockito.Mockito.times(2)).insert(taskCaptor.capture());
        List<ProcessActiveTaskEntity> tasks = taskCaptor.getAllValues();
        assertEquals(group.getId(), tasks.get(0).getTaskGroupId());
        assertEquals("branch-a-edge", tasks.get(0).getBranchKey());
        assertEquals(group.getId(), tasks.get(1).getTaskGroupId());
        assertEquals("branch-b-edge", tasks.get(1).getBranchKey());
        assertEquals(2, result.getCreatedTasks().size());
    }

    @Test
    void nestedParallelSplitIsRejectedInM2() {
        ProcessNodeDTO split = node("split", NodeTypeEnum.PARALLEL_SPLIT_GATEWAY);
        split.setPairedGatewayCode("join");
        ProcessNodeDTO join = node("join", NodeTypeEnum.PARALLEL_JOIN_GATEWAY);
        join.setPairedGatewayCode("split");

        RuntimeStateException error = assertThrows(RuntimeStateException.class,
                () -> advancer.advanceToNode(instance(), definition(nodes(split, join),
                        edges(edge("a", "split", "join"), edge("b", "split", "join"),
                                edge("after", "join", "split"))), "split", "parent", "parent-branch"));

        assertEquals(RuntimeErrorCodes.GATEWAY_CONFIG_INVALID, error.getErrorCode());
    }

    @Test
    void parallelJoinStopsPartialBranchAndOnlyFinalBranchContinues() {
        ProcessNodeDTO split = node("split", NodeTypeEnum.PARALLEL_SPLIT_GATEWAY);
        split.setPairedGatewayCode("join");
        ProcessNodeDTO join = node("join", NodeTypeEnum.PARALLEL_JOIN_GATEWAY);
        join.setPairedGatewayCode("split");
        ProcessDefinitionDetailDTO definition = definition(nodes(split, join, userTask("after")),
                edges(edge("a", "split", "join"), edge("b", "split", "join"), edge("after", "join", "after")));
        when(approverResolver.resolveApprovers(any()))
                .thenReturn(Collections.singletonList(new UserDTO("u-1", "User")));

        ProcessTaskGroupEntity partial = group("ACTIVE", 0L, 0, "{\"a\":\"RUNNING\",\"b\":\"RUNNING\"}");
        when(taskGroupRepository.findById("group-1")).thenReturn(partial);
        when(taskGroupRepository.markBranchArrived(eq("group-1"), eq("a"), anyLong())).thenReturn(1);

        RuntimeAdvanceResult partialResult = advancer.advanceToNode(instance(), definition, "join", "group-1", "a");
        assertTrue(partialResult.getCreatedTasks().isEmpty());
        verify(taskGroupRepository, org.mockito.Mockito.times(1)).findById("group-1");

        ProcessTaskGroupEntity finalGroup = group("ACTIVE", 1L, 1,
                "{\"a\":\"ARRIVED\",\"b\":\"RUNNING\"}");
        ProcessTaskGroupEntity completed = group("COMPLETED", 2L, 2,
                "{\"a\":\"ARRIVED\",\"b\":\"ARRIVED\"}");
        when(taskGroupRepository.findById("group-1")).thenReturn(finalGroup, completed);
        when(taskGroupRepository.markBranchArrived(eq("group-1"), eq("b"), anyLong())).thenReturn(1);

        RuntimeAdvanceResult finalResult = advancer.advanceToNode(instance(), definition, "join", "group-1", "b");
        assertEquals(1, finalResult.getCreatedTasks().size());
        assertEquals("after", finalResult.getCreatedTasks().get(0).getNodeCode());
    }

    @Test
    void nonFinalBranchDoesNotAdvanceWhenConcurrentBranchCompletesBeforeItCanReread() {
        ProcessNodeDTO split = node("split", NodeTypeEnum.PARALLEL_SPLIT_GATEWAY);
        split.setPairedGatewayCode("join");
        ProcessNodeDTO join = node("join", NodeTypeEnum.PARALLEL_JOIN_GATEWAY);
        join.setPairedGatewayCode("split");
        ProcessDefinitionDetailDTO definition = definition(nodes(split, join, userTask("after")),
                edges(edge("a", "split", "join"), edge("b", "split", "join"), edge("after", "join", "after")));
        ProcessTaskGroupEntity branchASnapshot = group("ACTIVE", 0L, 0,
                "{\"a\":\"RUNNING\",\"b\":\"RUNNING\"}");
        ProcessTaskGroupEntity concurrentCompleted = group("COMPLETED", 2L, 2,
                "{\"a\":\"ARRIVED\",\"b\":\"ARRIVED\"}");
        when(approverResolver.resolveApprovers(any()))
                .thenReturn(Collections.singletonList(new UserDTO("u-1", "User")));
        when(taskGroupRepository.findById("group-1")).thenReturn(branchASnapshot, concurrentCompleted);
        when(taskGroupRepository.markBranchArrived(eq("group-1"), eq("a"), anyLong())).thenReturn(1);

        RuntimeAdvanceResult result = advancer.advanceToNode(instance(), definition, "join", "group-1", "a");

        assertTrue(result.getCreatedTasks().isEmpty());
        verify(taskGroupRepository, org.mockito.Mockito.times(1)).findById("group-1");
        verify(activeTaskRepository, org.mockito.Mockito.never()).insert(any(ProcessActiveTaskEntity.class));
    }

    @Test
    void endCompletesOnlyWhenNoOpenTasksOrTaskGroupsRemain() {
        when(activeTaskRepository.countOpenByInstanceId("instance-1")).thenReturn(0L);
        when(taskGroupRepository.countActiveByInstanceId("instance-1")).thenReturn(0L);
        when(instanceRepository.complete(eq("instance-1"), any())).thenReturn(1);

        RuntimeAdvanceResult completed = advancer.advanceToNode(instance(), definition(
                nodes(node("end", NodeTypeEnum.END)), edges()), "end", null, null);

        assertTrue(completed.isInstanceCompleted());
        verify(instanceRepository).updateCurrentNodeCodes("instance-1", "[]");

        when(activeTaskRepository.countOpenByInstanceId("instance-1")).thenReturn(1L);
        RuntimeStateException blocked = assertThrows(RuntimeStateException.class,
                () -> advancer.advanceToNode(instance(), definition(
                        nodes(node("end", NodeTypeEnum.END)), edges()), "end", null, null));
        assertEquals(RuntimeErrorCodes.INVALID_ACTION, blocked.getErrorCode());
    }

    private ProcessInstanceEntity instance() {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        instance.setId("instance-1");
        instance.setDefinitionId("definition-1");
        instance.setStarterUserId("starter-1");
        instance.setStarterDeptId("dept-1");
        instance.setVariablesJson("{}");
        return instance;
    }

    private ProcessTaskGroupEntity group(String status, long version, int completedCount, String branches) {
        ProcessTaskGroupEntity group = new ProcessTaskGroupEntity();
        group.setId("group-1");
        group.setInstanceId("instance-1");
        group.setNodeCode("split");
        group.setJoinNodeCode("join");
        group.setGroupType("PARALLEL_GATEWAY");
        group.setTotalCount(Integer.valueOf(2));
        group.setCompletedCount(Integer.valueOf(completedCount));
        group.setBranchStateJson(branches);
        group.setGroupStatus(status);
        group.setLockVersion(Long.valueOf(version));
        return group;
    }

    private ProcessDefinitionDetailDTO definition(List<ProcessNodeDTO> nodes, List<ProcessEdgeDTO> edges) {
        ProcessDefinitionDetailDTO definition = new ProcessDefinitionDetailDTO();
        definition.setId("definition-1");
        definition.setNodes(nodes);
        definition.setEdges(edges);
        return definition;
    }

    private List<ProcessNodeDTO> nodes(ProcessNodeDTO... nodes) {
        return Arrays.asList(nodes);
    }

    private List<ProcessEdgeDTO> edges(ProcessEdgeDTO... edges) {
        return Arrays.asList(edges);
    }

    private ProcessNodeDTO userTask(String code) {
        ProcessNodeDTO node = node(code, NodeTypeEnum.USER_TASK);
        node.setNodeName(Character.toUpperCase(code.charAt(0)) + code.substring(1));
        node.setApproverRuleType(ApproverRuleTypeEnum.USER);
        node.setApproverRuleConfig("{\"userIds\":[\"user-1\"]}");
        node.setMultiInstanceMode(MultiInstanceModeEnum.SINGLE);
        return node;
    }

    private ProcessNodeDTO node(String code, NodeTypeEnum type) {
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setNodeCode(code);
        node.setNodeType(type);
        return node;
    }

    private ProcessEdgeDTO edge(String code, String source, String target) {
        ProcessEdgeDTO edge = new ProcessEdgeDTO();
        edge.setEdgeCode(code);
        edge.setSourceNodeCode(source);
        edge.setTargetNodeCode(target);
        return edge;
    }

    private ProcessEdgeDTO conditionalEdge(String code, String source, String target, String expression) {
        ProcessEdgeDTO edge = edge(code, source, target);
        edge.setConditionExpression(expression);
        return edge;
    }

    private static final class MapBackedOrganizationProvider implements OrganizationProvider {
        private final Map<String, UserDTO> users = new LinkedHashMap<String, UserDTO>();

        void addUser(String userId, String userName) {
            users.put(userId, new UserDTO(userId, userName));
        }

        @Override
        public List<DepartmentDTO> listDepartments() {
            return Collections.emptyList();
        }

        @Override
        public List<UserDTO> listUsersByDepartment(String departmentId) {
            return Collections.emptyList();
        }

        @Override
        public List<UserDTO> listUsersByRole(String roleCode) {
            return Collections.emptyList();
        }

        @Override
        public List<UserDTO> listUsersByRoleAndDepartment(String roleCode, String departmentId) {
            return Collections.emptyList();
        }

        @Override
        public Optional<UserDTO> findUser(String userId) {
            return Optional.ofNullable(users.get(userId));
        }

        @Override
        public Optional<DepartmentDTO> findDepartment(String departmentId) {
            return Optional.empty();
        }
    }
}

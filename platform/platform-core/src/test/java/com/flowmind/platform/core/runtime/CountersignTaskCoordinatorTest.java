package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.entity.ProcessTaskGroupEntity;
import com.flowmind.platform.persistence.repository.TaskGroupRepository;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** M6 定义级会签计数与唯一推进测试。 */
class CountersignTaskCoordinatorTest {

    @Test
    void partialApprovalOnlyIncrementsGroupAndDoesNotAdvance() {
        Fixture fixture = fixture(group("ACTIVE", 0, 2, 0L));
        CountersignTaskCoordinator.CountersignAdvanceContext context = fixture.coordinator.prepare(
                fixture.instance, fixture.definition, fixture.task, fixture.node);
        when(fixture.groups.incrementCompletedCount("group-1", 0L)).thenReturn(1);

        RuntimeAdvanceResult result = fixture.coordinator.completeAndAdvance(context);

        assertTrue(result.getCreatedTasks().isEmpty());
        verify(fixture.advancer, never()).advanceToNode(any(ProcessInstanceEntity.class),
                any(ProcessDefinitionDetailDTO.class), any(String.class), any(String.class), any(String.class),
                any(RuntimeAdvancePreparation.class));
    }

    @Test
    void retriesStaleGroupVersionAndOnlyFinalCounterAdvancesWithParentContext() {
        ProcessTaskGroupEntity initial = group("ACTIVE", 0, 2, 0L);
        ProcessTaskGroupEntity refreshed = group("ACTIVE", 1, 2, 1L);
        Fixture fixture = fixture(initial);
        when(fixture.groups.findById("group-1")).thenReturn(initial, refreshed);
        CountersignTaskCoordinator.CountersignAdvanceContext context = fixture.coordinator.prepare(
                fixture.instance, fixture.definition, fixture.task, fixture.node);
        when(fixture.groups.incrementCompletedCount("group-1", 0L)).thenReturn(0);
        when(fixture.groups.incrementCompletedCount("group-1", 1L)).thenReturn(1);
        RuntimeAdvanceResult advanced = new RuntimeAdvanceResult();
        when(fixture.advancer.advanceToNode(fixture.instance, fixture.definition, "after",
                "parallel-1", "branch-a", fixture.preparation)).thenReturn(advanced);

        assertEquals(advanced, fixture.coordinator.completeAndAdvance(context));
        verify(fixture.advancer).advanceToNode(fixture.instance, fixture.definition, "after",
                "parallel-1", "branch-a", fixture.preparation);
    }

    @Test
    void terminalGroupDuringRetryFailsWithFrozenConflictCode() {
        ProcessTaskGroupEntity initial = group("ACTIVE", 0, 2, 0L);
        ProcessTaskGroupEntity canceled = group("CANCELED", 0, 2, 1L);
        Fixture fixture = fixture(initial);
        when(fixture.groups.findById("group-1")).thenReturn(initial, canceled);
        CountersignTaskCoordinator.CountersignAdvanceContext context = fixture.coordinator.prepare(
                fixture.instance, fixture.definition, fixture.task, fixture.node);
        when(fixture.groups.incrementCompletedCount("group-1", 0L)).thenReturn(0);

        RuntimeStateException error = assertThrows(RuntimeStateException.class,
                () -> fixture.coordinator.completeAndAdvance(context));

        assertEquals(RuntimeErrorCodes.TASK_GROUP_CONCURRENT_MODIFIED, error.getErrorCode());
    }

    @Test
    void retryExhaustionFailsWithoutAdvancing() {
        ProcessTaskGroupEntity initial = group("ACTIVE", 0, 2, 0L);
        ProcessTaskGroupEntity versionOne = group("ACTIVE", 0, 2, 1L);
        ProcessTaskGroupEntity versionTwo = group("ACTIVE", 0, 2, 2L);
        ProcessTaskGroupEntity versionThree = group("ACTIVE", 0, 2, 3L);
        Fixture fixture = fixture(initial);
        when(fixture.groups.findById("group-1"))
                .thenReturn(initial, versionOne, versionTwo, versionThree);
        CountersignTaskCoordinator.CountersignAdvanceContext context = fixture.coordinator.prepare(
                fixture.instance, fixture.definition, fixture.task, fixture.node);

        RuntimeStateException error = assertThrows(RuntimeStateException.class,
                () -> fixture.coordinator.completeAndAdvance(context));

        assertEquals(RuntimeErrorCodes.TASK_GROUP_CONCURRENT_MODIFIED, error.getErrorCode());
        verify(fixture.groups).incrementCompletedCount("group-1", 0L);
        verify(fixture.groups).incrementCompletedCount("group-1", 1L);
        verify(fixture.groups).incrementCompletedCount("group-1", 2L);
        verify(fixture.advancer, never()).advanceToNode(any(ProcessInstanceEntity.class),
                any(ProcessDefinitionDetailDTO.class), any(String.class), any(String.class), any(String.class),
                any(RuntimeAdvancePreparation.class));
    }

    private Fixture fixture(ProcessTaskGroupEntity group) {
        Fixture fixture = new Fixture();
        fixture.groups = mock(TaskGroupRepository.class);
        fixture.advancer = mock(RuntimeNodeAdvancer.class);
        fixture.coordinator = new CountersignTaskCoordinator(fixture.groups, fixture.advancer);
        fixture.instance = new ProcessInstanceEntity();
        fixture.instance.setId("instance-1");
        fixture.task = new ProcessActiveTaskEntity();
        fixture.task.setId("task-1");
        fixture.task.setInstanceId("instance-1");
        fixture.task.setNodeCode("review");
        fixture.task.setTaskGroupId("group-1");
        fixture.node = new ProcessNodeDTO();
        fixture.node.setNodeCode("review");
        fixture.node.setNodeType(NodeTypeEnum.USER_TASK);
        fixture.node.setMultiInstanceMode(MultiInstanceModeEnum.COUNTERSIGN);
        ProcessNodeDTO after = new ProcessNodeDTO();
        after.setNodeCode("after");
        after.setNodeType(NodeTypeEnum.USER_TASK);
        ProcessEdgeDTO edge = new ProcessEdgeDTO();
        edge.setEdgeCode("review-after");
        edge.setSourceNodeCode("review");
        edge.setTargetNodeCode("after");
        fixture.definition = new ProcessDefinitionDetailDTO();
        fixture.definition.setNodes(java.util.Arrays.asList(fixture.node, after));
        fixture.definition.setEdges(Collections.singletonList(edge));
        fixture.preparation = new RuntimeAdvancePreparation(
                Collections.singletonMap("after", Collections.singletonList("user-2")),
                Collections.<String, String>emptyMap());
        when(fixture.groups.findById("group-1")).thenReturn(group);
        when(fixture.advancer.prepareAdvance(fixture.instance, fixture.definition, "after",
                "parallel-1", "branch-a")).thenReturn(fixture.preparation);
        return fixture;
    }

    private ProcessTaskGroupEntity group(String status, int completed, int total, long version) {
        ProcessTaskGroupEntity group = new ProcessTaskGroupEntity();
        group.setId("group-1");
        group.setInstanceId("instance-1");
        group.setNodeCode("review");
        group.setGroupType("COUNTERSIGN");
        group.setGroupStatus(status);
        group.setCompletedCount(Integer.valueOf(completed));
        group.setTotalCount(Integer.valueOf(total));
        group.setLockVersion(Long.valueOf(version));
        group.setBranchStateJson("{}");
        group.setParentGroupId("parallel-1");
        group.setParentBranchKey("branch-a");
        return group;
    }

    private static final class Fixture {
        private TaskGroupRepository groups;
        private RuntimeNodeAdvancer advancer;
        private CountersignTaskCoordinator coordinator;
        private ProcessInstanceEntity instance;
        private ProcessActiveTaskEntity task;
        private ProcessNodeDTO node;
        private ProcessDefinitionDetailDTO definition;
        private RuntimeAdvancePreparation preparation;
    }
}

package com.flowmind.business.workflow;

import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDetailDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.enums.InstanceStatusEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.api.enums.TaskStatusEnum;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 解析已办记录对应的撤回资格；结果仅用于展示，Platform 动作接口仍负责最终校验。
 */
public class WorkflowWithdrawContextResolver {
    private static final Set<String> SOURCE_ACTIONS = new HashSet<String>(Arrays.asList(
            "SEND", "APPROVE", "REJECT", "RETURN", "DIRECT_SEND"));

    public Resolution resolve(ProcessInstanceDetailDTO instance, String userId) {
        return resolve(instance, null, userId);
    }

    public Resolution resolve(ProcessInstanceDetailDTO instance, ProcessDefinitionDetailDTO definition,
                              String userId) {
        if (instance == null || !InstanceStatusEnum.RUNNING.equals(instance.getInstanceStatus())) return null;
        List<TaskDTO> activeTasks = new ArrayList<TaskDTO>(safe(instance.getActiveTasks()));
        Collections.sort(activeTasks, new Comparator<TaskDTO>() {
            @Override
            public int compare(TaskDTO left, TaskDTO right) {
                if (left == right) return 0;
                if (left == null) return 1;
                if (right == null) return -1;
                if (left.getCreatedAt() == null && right.getCreatedAt() != null) return 1;
                if (left.getCreatedAt() != null && right.getCreatedAt() == null) return -1;
                if (left.getCreatedAt() != null) {
                    int created = left.getCreatedAt().compareTo(right.getCreatedAt());
                    if (created != 0) return created;
                }
                String leftId = left.getTaskId() == null ? "" : left.getTaskId();
                String rightId = right.getTaskId() == null ? "" : right.getTaskId();
                return leftId.compareTo(rightId);
            }
        });
        if (activeTasks.isEmpty()) return null;
        for (TaskDTO activeTask : activeTasks) {
            if (!isOpenVersionedTask(activeTask)) return null;
        }
        TaskDTO task = activeTasks.get(0);
        if (!isSerialTaskSet(activeTasks) && !isTopLevelOrSignTaskSet(activeTasks, definition)) return null;

        List<HistoryTaskDTO> histories = new ArrayList<HistoryTaskDTO>(safe(instance.getHistoryTasks()));
        final Set<String> immediatePreviousNodes = immediatePreviousUserNodes(definition, task.getNodeCode());
        Collections.sort(histories, new Comparator<HistoryTaskDTO>() {
            @Override
            public int compare(HistoryTaskDTO left, HistoryTaskDTO right) {
                if (left == right) return 0;
                if (left == null) return 1;
                if (right == null) return -1;
                if (left.getCompletedAt() == null && right.getCompletedAt() != null) return 1;
                if (left.getCompletedAt() != null && right.getCompletedAt() == null) return -1;
                if (left.getCompletedAt() != null) {
                    int completed = right.getCompletedAt().compareTo(left.getCompletedAt());
                    if (completed != 0) return completed;
                }
                boolean leftPrevious = immediatePreviousNodes.contains(left.getNodeCode());
                boolean rightPrevious = immediatePreviousNodes.contains(right.getNodeCode());
                if (leftPrevious != rightPrevious) return leftPrevious ? -1 : 1;
                if (left.getStartedAt() == null && right.getStartedAt() != null) return 1;
                if (left.getStartedAt() != null && right.getStartedAt() == null) return -1;
                if (left.getStartedAt() != null) {
                    int started = right.getStartedAt().compareTo(left.getStartedAt());
                    if (started != 0) return started;
                }
                String leftId = left.getHistoryTaskId() == null ? "" : left.getHistoryTaskId();
                String rightId = right.getHistoryTaskId() == null ? "" : right.getHistoryTaskId();
                return rightId.compareTo(leftId);
            }
        });
        Set<String> activeTaskIds = new HashSet<String>();
        for (TaskDTO activeTask : activeTasks) activeTaskIds.add(activeTask.getTaskId());
        for (HistoryTaskDTO history : histories) {
            if (history == null || activeTaskIds.contains(history.getActiveTaskId())) continue;
            String action = history.getActionType() == null ? null : history.getActionType().name();
            if (!SOURCE_ACTIONS.contains(action)) continue;
            // 撤回后重建同一申请节点时，不再把原提交历史展示为可再次撤回。
            if (task.getNodeCode() != null && task.getNodeCode().equals(history.getNodeCode())) return null;
            if (userId == null || !userId.equals(history.getAssigneeUserId())) return null;
            return new Resolution(task, history);
        }
        return null;
    }

    private boolean isOpenVersionedTask(TaskDTO task) {
        return task != null && (TaskStatusEnum.ACTIVE.equals(task.getTaskStatus())
                || TaskStatusEnum.CLAIMED.equals(task.getTaskStatus()))
                && hasText(task.getTaskId()) && task.getTaskVersion() != null;
    }

    private boolean isSerialTaskSet(List<TaskDTO> tasks) {
        if (tasks.size() != 1) return false;
        TaskDTO task = tasks.get(0);
        return !hasText(task.getTaskGroupId()) && !hasText(task.getBranchKey());
    }

    private boolean isTopLevelOrSignTaskSet(List<TaskDTO> tasks, ProcessDefinitionDetailDTO definition) {
        TaskDTO first = tasks.get(0);
        if (!hasText(first.getTaskGroupId()) || hasText(first.getBranchKey()) || !hasText(first.getNodeCode())) {
            return false;
        }
        for (TaskDTO task : tasks) {
            if (!first.getTaskGroupId().equals(task.getTaskGroupId())
                    || !first.getNodeCode().equals(task.getNodeCode()) || hasText(task.getBranchKey())) return false;
        }
        for (ProcessNodeDTO node : definition == null
                ? Collections.<ProcessNodeDTO>emptyList() : safe(definition.getNodes())) {
            if (node != null && first.getNodeCode().equals(node.getNodeCode())) {
                return MultiInstanceModeEnum.OR_SIGN.equals(node.getMultiInstanceMode());
            }
        }
        return false;
    }

    private Set<String> immediatePreviousUserNodes(ProcessDefinitionDetailDTO definition, String currentNodeCode) {
        Set<String> result = new HashSet<String>();
        if (definition == null || !hasText(currentNodeCode)) return result;
        List<ProcessNodeDTO> nodes = safe(definition.getNodes());
        List<ProcessEdgeDTO> edges = safe(definition.getEdges());
        java.util.Map<String, ProcessNodeDTO> nodesByCode = new java.util.HashMap<String, ProcessNodeDTO>();
        for (ProcessNodeDTO node : nodes) {
            if (node != null && hasText(node.getNodeCode())) nodesByCode.put(node.getNodeCode(), node);
        }
        ArrayDeque<String> queue = new ArrayDeque<String>();
        Set<String> visited = new HashSet<String>();
        queue.add(currentNodeCode);
        while (!queue.isEmpty()) {
            String target = queue.removeFirst();
            if (!visited.add(target)) continue;
            for (ProcessEdgeDTO edge : edges) {
                if (edge == null || !target.equals(edge.getTargetNodeCode())
                        || !hasText(edge.getSourceNodeCode())) continue;
                ProcessNodeDTO source = nodesByCode.get(edge.getSourceNodeCode());
                if (source != null && NodeTypeEnum.USER_TASK.equals(source.getNodeType())) {
                    result.add(source.getNodeCode());
                } else {
                    queue.add(edge.getSourceNodeCode());
                }
            }
        }
        return result;
    }

    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
    private <T> List<T> safe(List<T> values) { return values == null ? Collections.<T>emptyList() : values; }

    public static final class Resolution {
        private final TaskDTO activeTask;
        private final HistoryTaskDTO sourceHistory;

        private Resolution(TaskDTO activeTask, HistoryTaskDTO sourceHistory) {
            this.activeTask = activeTask;
            this.sourceHistory = sourceHistory;
        }

        public TaskDTO getActiveTask() { return activeTask; }
        public HistoryTaskDTO getSourceHistory() { return sourceHistory; }
    }
}

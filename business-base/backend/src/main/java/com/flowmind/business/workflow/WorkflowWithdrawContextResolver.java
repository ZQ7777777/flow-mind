package com.flowmind.business.workflow;

import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDetailDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.enums.InstanceStatusEnum;
import com.flowmind.platform.api.enums.TaskStatusEnum;

import java.util.Arrays;
import java.util.ArrayList;
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
        if (instance == null || !InstanceStatusEnum.RUNNING.equals(instance.getInstanceStatus())) return null;
        List<TaskDTO> activeTasks = safe(instance.getActiveTasks());
        if (activeTasks.size() != 1) return null;
        TaskDTO task = activeTasks.get(0);
        if (task == null || (!TaskStatusEnum.ACTIVE.equals(task.getTaskStatus())
                && !TaskStatusEnum.CLAIMED.equals(task.getTaskStatus()))) return null;
        if (hasText(task.getTaskGroupId()) || hasText(task.getBranchKey())
                || !hasText(task.getTaskId()) || task.getTaskVersion() == null) return null;

        List<HistoryTaskDTO> histories = new ArrayList<HistoryTaskDTO>(safe(instance.getHistoryTasks()));
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
                String leftId = left.getHistoryTaskId() == null ? "" : left.getHistoryTaskId();
                String rightId = right.getHistoryTaskId() == null ? "" : right.getHistoryTaskId();
                return rightId.compareTo(leftId);
            }
        });
        for (HistoryTaskDTO history : histories) {
            if (history == null || task.getTaskId().equals(history.getActiveTaskId())) continue;
            String action = history.getActionType() == null ? null : history.getActionType().name();
            if (!SOURCE_ACTIONS.contains(action)) continue;
            // 撤回后重建同一申请节点时，不再把原提交历史展示为可再次撤回。
            if (task.getNodeCode() != null && task.getNodeCode().equals(history.getNodeCode())) return null;
            if (userId == null || !userId.equals(history.getAssigneeUserId())) return null;
            return new Resolution(task, history);
        }
        return null;
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

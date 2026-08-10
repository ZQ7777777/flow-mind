package com.flowmind.business.security;

import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class WorkflowAccessGuard {
    private final BusinessAuthorizationProvider authorizationProvider;

    public WorkflowAccessGuard(BusinessAuthorizationProvider authorizationProvider) {
        this.authorizationProvider = authorizationProvider;
    }

    /**
     * 校验当前用户是否可以查看流程详情。
     *
     * <p>允许发起人、当前候选人或办理人、历史办理人以及业务管理员访问；
     * 该方法只负责业务平台详情入口的读权限，最终流转动作仍由平台侧权限校验兜底。</p>
     */
    public void check(ProcessInstanceDTO instance, List<TaskDTO> activeTasks,
                      List<HistoryTaskDTO> historyTasks, String userId) {
        if (instance == null || !hasText(userId)) throw new BusinessAccessDeniedException("无权访问该流程实例");
        if (userId.equals(instance.getStarterUserId()) || authorizationProvider.isAdministrator(userId)) return;
        for (TaskDTO task : safe(activeTasks)) {
            if (userId.equals(task.getAssigneeUserId()) || safe(task.getCandidateUserIds()).contains(userId)) return;
        }
        for (HistoryTaskDTO task : safe(historyTasks)) {
            if (userId.equals(task.getAssigneeUserId())) return;
        }
        throw new BusinessAccessDeniedException("无权访问该流程实例");
    }

    /**
     * 判断字符串是否包含有效文本。
     */
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }

    /**
     * 将空列表归一化，避免权限遍历时出现空指针。
     */
    private <T> List<T> safe(List<T> values) { return values == null ? Collections.<T>emptyList() : values; }
}

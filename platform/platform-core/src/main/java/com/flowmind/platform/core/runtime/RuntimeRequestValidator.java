package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.enums.InstanceStatusEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.TaskStatusEnum;
import com.flowmind.platform.api.request.ApproverResolveRequest;
import com.flowmind.platform.api.request.StartProcessRequest;
import com.flowmind.platform.api.request.TaskOperationRequest;
import com.flowmind.platform.api.request.UpdateVariablesRequest;
import com.flowmind.platform.api.spi.ApproverResolver;
import com.flowmind.platform.api.spi.CurrentUserProvider;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时请求、身份、实例状态和任务权限的统一校验器。
 *
 * @author FlowMind
 * @since 2026-07-22
 */
@Component
public class RuntimeRequestValidator {

    /** 获取可信当前用户的 SPI。 */
    private final CurrentUserProvider currentUserProvider;

    public RuntimeRequestValidator(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * 校验启动请求并返回当前用户快照，供后续固化发起人名称。
     *
     * @param request 启动请求
     * @return 可信当前用户
     */
    public UserContext validateStart(StartProcessRequest request) {
        requireOperationId(request);
        requireText(request.getProcessCode(), "processCode");
        requireText(request.getInstanceTitle(), "instanceTitle");
        requireText(request.getStarterUserId(), "starterUserId");
        UserContext currentUser = currentUser();
        requireCurrentUserMatches(request.getStarterUserId(), currentUser, "starterUserId");
        if (hasText(request.getStarterDeptId()) && !request.getStarterDeptId().equals(currentUser.getDepartmentId())) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION,
                    "starterDeptId does not match current user department");
        }
        return currentUser;
    }

    /**
     * 校验变量更新请求、当前用户和允许更新的实例状态。
     *
     * @param request 变量更新请求
     * @param instance 已加载流程实例
     * @return 可信当前用户
     */
    public UserContext validateVariableUpdate(UpdateVariablesRequest request, ProcessInstanceEntity instance) {
        requireOperationId(request);
        requireText(request.getInstanceId(), "instanceId");
        requireText(request.getOperatorUserId(), "operatorUserId");
        if (request.getVariables() == null) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION, "variables are required");
        }
        UserContext currentUser = currentUser();
        requireCurrentUserMatches(request.getOperatorUserId(), currentUser, "operatorUserId");
        if (instance == null || !request.getInstanceId().equals(instance.getId())) {
            throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION, "process instance does not exist");
        }
        if (!InstanceStatusEnum.NOT_STARTED.name().equals(instance.getInstanceStatus())
                && !InstanceStatusEnum.RUNNING.name().equals(instance.getInstanceStatus())) {
            throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION,
                    "variables cannot be updated for the current instance status");
        }
        return currentUser;
    }

    /**
     * 校验任务动作请求、实例和任务状态、候选人或受理人权限以及乐观锁版本。
     *
     * @param request 任务动作请求
     * @param instance 任务所属流程实例
     * @param task     当前活动任务
     * @return 可信当前用户
     */
    public UserContext validateTaskAction(TaskOperationRequest request,
                                          ProcessInstanceEntity instance,
                                          ProcessActiveTaskEntity task) {
        requireOperationId(request);
        requireText(request.getTaskId(), "taskId");
        requireText(request.getOperatorUserId(), "operatorUserId");
        if (request.getExpectedTaskVersion() == null) {
            throw new RuntimeValidationException(RuntimeErrorCodes.TASK_CONCURRENT_MODIFIED,
                    "expectedTaskVersion is required");
        }
        UserContext currentUser = currentUser();
        requireCurrentUserMatches(request.getOperatorUserId(), currentUser, "operatorUserId");
        if (task == null || !request.getTaskId().equals(task.getId())) {
            throw new RuntimeStateException(RuntimeErrorCodes.TASK_NOT_FOUND, "active task does not exist");
        }
        if (instance == null || !task.getInstanceId().equals(instance.getId())) {
            throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION, "task instance does not exist");
        }
        if (!InstanceStatusEnum.RUNNING.name().equals(instance.getInstanceStatus())) {
            throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION, "process instance is not running");
        }
        if (!TaskStatusEnum.ACTIVE.name().equals(task.getTaskStatus())
                && !TaskStatusEnum.CLAIMED.name().equals(task.getTaskStatus())) {
            throw new RuntimeStateException(RuntimeErrorCodes.TASK_NOT_ACTIVE, "task is not active");
        }
        if (task.getLockVersion() == null || !task.getLockVersion().equals(request.getExpectedTaskVersion())) {
            throw new RuntimeStateException(RuntimeErrorCodes.TASK_CONCURRENT_MODIFIED,
                    "expectedTaskVersion does not match active task");
        }
        assertTaskPermission(task, currentUser.getUserId());
        return currentUser;
    }

    /**
     * 解析并按用户 ID 去重普通用户任务的候选人；M2 仅支持 SINGLE。
     *
     * @param multiInstanceMode 节点多人处理模式
     * @param resolver          审批人解析 SPI
     * @param request           审批人解析请求
     * @return 保持首次出现顺序的非空候选人列表
     */
    public List<UserDTO> resolveApprovers(MultiInstanceModeEnum multiInstanceMode,
                                          ApproverResolver resolver,
                                          ApproverResolveRequest request) {
        if (!MultiInstanceModeEnum.SINGLE.equals(multiInstanceMode)) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION,
                    "M2 only supports SINGLE user-task mode");
        }
        if (resolver == null || request == null) {
            throw new RuntimeValidationException(RuntimeErrorCodes.APPROVER_RESOLVE_FAILED,
                    "approver resolver and request are required");
        }
        List<UserDTO> resolved;
        try {
            resolved = resolver.resolveApprovers(request);
        } catch (RuntimeException ex) {
            throw new RuntimeStateException(RuntimeErrorCodes.APPROVER_RESOLVE_FAILED,
                    "approver resolver failed: " + ex.getMessage());
        }
        Map<String, UserDTO> usersById = new LinkedHashMap<String, UserDTO>();
        if (resolved != null) {
            for (UserDTO user : resolved) {
                if (user != null && hasText(user.getUserId()) && !usersById.containsKey(user.getUserId())) {
                    usersById.put(user.getUserId(), new UserDTO(user.getUserId(), user.getUserName()));
                }
            }
        }
        if (usersById.isEmpty()) {
            throw new RuntimeStateException(RuntimeErrorCodes.APPROVER_RESOLVE_FAILED,
                    "approver resolver returned no valid user");
        }
        return new ArrayList<UserDTO>(usersById.values());
    }

    /** 校验 ACTIVE 使用候选人权限、CLAIMED 使用受理人权限。 */
    private void assertTaskPermission(ProcessActiveTaskEntity task, String userId) {
        if (TaskStatusEnum.CLAIMED.name().equals(task.getTaskStatus())) {
            if (!userId.equals(task.getAssigneeUserId())) {
                throw new RuntimeValidationException(RuntimeErrorCodes.TASK_PERMISSION_DENIED,
                        "current user is not the task assignee");
            }
            return;
        }
        if (hasText(task.getAssigneeUserId())) {
            if (!userId.equals(task.getAssigneeUserId())) {
                throw new RuntimeValidationException(RuntimeErrorCodes.TASK_PERMISSION_DENIED,
                        "current user is not the task assignee");
            }
            return;
        }
        try {
            if (!RuntimeJsonCodec.readStringList(task.getCandidateUserIds()).contains(userId)) {
                throw new RuntimeValidationException(RuntimeErrorCodes.TASK_PERMISSION_DENIED,
                        "current user is not a task candidate");
            }
        } catch (IllegalArgumentException ex) {
            if (ex instanceof RuntimeValidationException) {
                throw (RuntimeValidationException) ex;
            }
            throw new RuntimeStateException(RuntimeErrorCodes.DEFINITION_INVALID,
                    "task candidate users are malformed");
        }
    }

    private void requireOperationId(com.flowmind.platform.api.request.OperationRequest request) {
        if (request == null) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION, "operation request is required");
        }
        if (!hasText(request.getOperationId())) {
            throw new RuntimeValidationException(RuntimeErrorCodes.OPERATION_ID_REQUIRED, "operationId is required");
        }
    }

    private UserContext currentUser() {
        UserContext currentUser = currentUserProvider == null ? null : currentUserProvider.getCurrentUser();
        if (currentUser == null || !hasText(currentUser.getUserId())) {
            throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION, "current user is unavailable");
        }
        return currentUser;
    }

    private void requireCurrentUserMatches(String requestedUserId, UserContext currentUser, String fieldName) {
        if (!requestedUserId.equals(currentUser.getUserId())) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION,
                    fieldName + " does not match current user");
        }
    }

    private void requireText(String value, String fieldName) {
        if (!hasText(value)) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION, fieldName + " is required");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

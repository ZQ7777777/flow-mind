package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.enums.InstanceStatusEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.TaskStatusEnum;
import com.flowmind.platform.api.request.ApproverResolveRequest;
import com.flowmind.platform.api.request.OperationRequest;
import com.flowmind.platform.api.request.StartProcessRequest;
import com.flowmind.platform.api.request.TaskOperationRequest;
import com.flowmind.platform.api.request.UpdateVariablesRequest;
import com.flowmind.platform.api.spi.ApproverResolver;
import com.flowmind.platform.api.spi.CurrentUserProvider;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
        UserContext currentUser = validateVariableUpdateIdentity(request);
        validateVariableUpdate(request, instance, currentUser);
        return currentUser;
    }

    /**
     * 仅校验变量更新请求自身及操作人身份。
     *
     * <p>调用方应在创建幂等决定前调用本方法，这样成功重放无需依赖实例仍处于可更新状态。
     * 首次执行时仍必须继续调用 {@link #validateVariableUpdate(UpdateVariablesRequest,
     * ProcessInstanceEntity, UserContext)} 校验实例状态。</p>
     *
     * @param request 变量更新请求
     * @return 已校验的可信当前用户
     */
    public UserContext validateVariableUpdateIdentity(UpdateVariablesRequest request) {
        requireOperationId(request);
        requireText(request.getInstanceId(), "instanceId");
        requireText(request.getOperatorUserId(), "operatorUserId");
        if (request.getVariables() == null) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION, "variables are required");
        }
        UserContext currentUser = currentUser();
        requireCurrentUserMatches(request.getOperatorUserId(), currentUser, "operatorUserId");
        return currentUser;
    }

    /**
     * 校验实例级管理动作的公共请求字段和可信操作人身份。
     *
     * <p>M3 尚未引入独立的管理员授权 SPI，因此这里只保证请求中的操作人不能伪造；
     * 角色授权由宿主适配层在进入平台前负责。</p>
     *
     * @param request        带操作幂等号的请求
     * @param instanceId      目标流程实例 ID
     * @param operatorUserId 请求声明的操作人 ID
     * @return 可信当前用户
     */
    public UserContext validateInstanceOperationIdentity(OperationRequest request,
                                                         String instanceId,
                                                         String operatorUserId) {
        requireOperationId(request);
        requireText(instanceId, "instanceId");
        requireText(operatorUserId, "operatorUserId");
        UserContext currentUser = currentUser();
        requireCurrentUserMatches(operatorUserId, currentUser, "operatorUserId");
        return currentUser;
    }

    /**
     * 校验变量更新目标实例及其运行状态，调用方已经完成身份校验时使用。
     *
     * @param request     变量更新请求
     * @param instance    已加载流程实例
     * @param currentUser 已校验的可信当前用户
     */
    public void validateVariableUpdate(UpdateVariablesRequest request,
                                        ProcessInstanceEntity instance,
                                        UserContext currentUser) {
        if (currentUser == null) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION, "current user is required");
        }
        if (instance == null || !request.getInstanceId().equals(instance.getId())) {
            throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION, "process instance does not exist");
        }
        if (!InstanceStatusEnum.NOT_STARTED.name().equals(instance.getInstanceStatus())
                && !InstanceStatusEnum.RUNNING.name().equals(instance.getInstanceStatus())) {
            throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION,
                    "variables cannot be updated for the current instance status");
        }
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
        UserContext currentUser = validateTaskIdentity(request);
        validateTaskAction(request, instance, task, currentUser);
        return currentUser;
    }

    /**
     * 仅校验任务动作请求自身及操作人身份。
     *
     * <p>成功幂等重放发生时，原活动任务已经进入终态，因此必须先完成本校验并读取幂等记录，
     * 不能要求原任务仍为活动状态。</p>
     *
     * @param request 任务动作请求
     * @return 已校验的可信当前用户
     */
    public UserContext validateTaskIdentity(TaskOperationRequest request) {
        requireOperationId(request);
        requireText(request.getTaskId(), "taskId");
        requireText(request.getOperatorUserId(), "operatorUserId");
        if (request.getExpectedTaskVersion() == null) {
            throw new RuntimeValidationException(RuntimeErrorCodes.TASK_CONCURRENT_MODIFIED,
                    "expectedTaskVersion is required");
        }
        UserContext currentUser = currentUser();
        requireCurrentUserMatches(request.getOperatorUserId(), currentUser, "operatorUserId");
        return currentUser;
    }

    /**
     * 校验任务、实例状态和候选人权限，调用方已经完成身份校验时使用。
     *
     * @param request     任务动作请求
     * @param instance    任务所属流程实例
     * @param task        当前活动任务
     * @param currentUser 已校验的可信当前用户
     */
    public void validateTaskAction(TaskOperationRequest request,
                                   ProcessInstanceEntity instance,
                                   ProcessActiveTaskEntity task,
                                   UserContext currentUser) {
        if (currentUser == null) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION, "current user is required");
        }
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
    }

    /**
     * 解析并按用户 ID 去重、排序用户任务的候选人，保留 SINGLE、OR_SIGN、COUNTERSIGN 的审批人集合语义。
     *
     * @param multiInstanceMode 节点多人处理模式
     * @param resolver          审批人解析 SPI
     * @param request           审批人解析请求
     * @return 按用户 ID 稳定排序的非空候选人列表
     */
    public List<UserDTO> resolveApprovers(MultiInstanceModeEnum multiInstanceMode,
                                           ApproverResolver resolver,
                                           ApproverResolveRequest request) {
        if (resolver == null || request == null) {
            throw new RuntimeValidationException(RuntimeErrorCodes.APPROVER_RESOLVE_FAILED,
                    "approver resolver and request are required");
        }
        MultiInstanceModeEnum resolvedMode = request.getMultiInstanceMode() == null
                ? multiInstanceMode : request.getMultiInstanceMode();
        if (resolvedMode == null) {
            throw new RuntimeValidationException(RuntimeErrorCodes.APPROVER_RESOLVE_FAILED,
                    "multiInstanceMode is required");
        }
        List<UserDTO> resolved;
        try {
            resolved = resolver.resolveApprovers(request);
        } catch (RuntimeException ex) {
            throw new RuntimeStateException(RuntimeErrorCodes.APPROVER_RESOLVE_FAILED,
                    "approver resolver failed: " + ex.getMessage());
        }
        Map<String, UserDTO> usersById = new TreeMap<String, UserDTO>();
        if (resolved != null) {
            for (UserDTO user : resolved) {
                if (user != null && hasText(user.getUserId()) && !Boolean.FALSE.equals(user.getActive())
                        && !usersById.containsKey(user.getUserId())) {
                    usersById.put(user.getUserId(), copyUser(user));
                }
            }
        }
        if (usersById.isEmpty()) {
            throw new RuntimeStateException(RuntimeErrorCodes.APPROVER_RESOLVE_FAILED,
                    "approver resolver returned no valid user");
        }
        return new ArrayList<UserDTO>(usersById.values());
    }

    private UserDTO copyUser(UserDTO source) {
        UserDTO target = new UserDTO(source.getUserId(), source.getUserName());
        target.setDepartmentId(source.getDepartmentId());
        target.setDepartmentName(source.getDepartmentName());
        target.setRoleCodes(source.getRoleCodes() == null ? null : new ArrayList<String>(source.getRoleCodes()));
        target.setActive(source.getActive());
        return target;
    }

    /** 校验 ACTIVE 使用候选人权限、CLAIMED 使用受理人权限。 */
    private void assertTaskPermission(ProcessActiveTaskEntity task, String userId) {
        if (TaskStatusEnum.CLAIMED.name().equals(task.getTaskStatus())) {
            if (userId.equals(task.getAssigneeUserId())) {
                return;
            }
            throw new RuntimeValidationException(RuntimeErrorCodes.TASK_PERMISSION_DENIED,
                    "current user is not the task assignee");
        }
        if (hasText(task.getAssigneeUserId())) {
            if (userId.equals(task.getAssigneeUserId())) {
                return;
            }
            throw new RuntimeValidationException(RuntimeErrorCodes.TASK_PERMISSION_DENIED,
                    "current user is not the task assignee");
        }
        try {
            List<String> candidates = RuntimeJsonCodec.readStringList(task.getCandidateUserIds());
            if (candidates.contains(userId)) {
                return;
            }
            throw new RuntimeValidationException(RuntimeErrorCodes.TASK_PERMISSION_DENIED,
                    "current user is not a task candidate");
        } catch (IllegalArgumentException ex) {
            if (ex instanceof RuntimeValidationException) {
                throw (RuntimeValidationException) ex;
            }
            throw new RuntimeStateException(RuntimeErrorCodes.DEFINITION_INVALID,
                    "task candidate users are malformed");
        }
    }

    private void requireOperationId(OperationRequest request) {
        if (request == null) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION, "operation request is required");
        }
        if (!hasText(request.getOperationId())) {
            throw new RuntimeValidationException(RuntimeErrorCodes.OPERATION_ID_REQUIRED, "operationId is required");
        }
    }

    private UserContext currentUser() {
        UserContext systemOperator = SystemOperatorContext.current();
        if (systemOperator != null && hasText(systemOperator.getUserId())) {
            return systemOperator;
        }
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

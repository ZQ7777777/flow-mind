package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.InstanceStatusEnum;
import com.flowmind.platform.api.enums.TaskStatusEnum;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.entity.ProcessTaskGroupEntity;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import com.flowmind.platform.persistence.repository.TaskGroupRepository;
import org.springframework.stereotype.Service;

/**
 * 运行期只读状态校验组件。它不替代后续 Repository CAS 并发控制。
 */
@Service
public class RuntimeStateValidator {

    private final ActiveTaskRepository activeTaskRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final TaskGroupRepository taskGroupRepository;

    public RuntimeStateValidator(ActiveTaskRepository activeTaskRepository,
                                 ProcessInstanceRepository processInstanceRepository,
                                 TaskGroupRepository taskGroupRepository) {
        this.activeTaskRepository = activeTaskRepository;
        this.processInstanceRepository = processInstanceRepository;
        this.taskGroupRepository = taskGroupRepository;
    }

    /**
     * 校验任务动作前置状态，返回已读取的实例和活动任务供业务层复用。
     */
    public RuntimeTaskContext validateTaskAction(String taskId,
                                                 Long expectedTaskVersion,
                                                 ActionTypeEnum actionType,
                                                 UserContext operator) {
        validateOperator(operator);
        if (taskId == null || taskId.trim().isEmpty()) {
            throw new RuntimeValidationException(RuntimeErrorCodes.TASK_NOT_FOUND, "taskId is required");
        }
        ProcessActiveTaskEntity task = activeTaskRepository.findById(taskId);
        if (task == null) {
            throw new RuntimeValidationException(RuntimeErrorCodes.TASK_NOT_FOUND, "task does not exist");
        }
        ProcessInstanceEntity instance = processInstanceRepository.findById(task.getInstanceId());
        if (instance == null) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INSTANCE_NOT_FOUND, "instance does not exist");
        }
        requireRunning(instance);
        validateTaskStatus(task, actionType);
        validateTaskVersion(task, expectedTaskVersion);
        return new RuntimeTaskContext(instance, task, actionType, operator);
    }

    /**
     * 校验实例动作前置状态，返回已读取实例供业务层复用。
     */
    public RuntimeInstanceContext validateInstanceAction(String instanceId,
                                                         ActionTypeEnum actionType,
                                                         UserContext operator) {
        validateOperator(operator);
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INSTANCE_NOT_FOUND, "instanceId is required");
        }
        ProcessInstanceEntity instance = processInstanceRepository.findById(instanceId);
        if (instance == null) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INSTANCE_NOT_FOUND, "instance does not exist");
        }
        if (ActionTypeEnum.START.equals(actionType)) {
            if (!InstanceStatusEnum.NOT_STARTED.name().equals(instance.getInstanceStatus())) {
                throw new RuntimeValidationException(RuntimeErrorCodes.INSTANCE_STATUS_INVALID,
                        "instance status does not allow start");
            }
        } else {
            requireRunning(instance);
        }
        return new RuntimeInstanceContext(instance, actionType, operator);
    }

    /**
     * 校验任务组状态和版本，供后续并行/会签组件复用。
     */
    public ProcessTaskGroupEntity validateTaskGroupState(String taskGroupId,
                                                         Long expectedGroupVersion,
                                                         ActionTypeEnum actionType) {
        if (taskGroupId == null || taskGroupId.trim().isEmpty()) {
            throw new RuntimeValidationException(RuntimeErrorCodes.TASK_GROUP_NOT_FOUND,
                    "taskGroupId is required");
        }
        ProcessTaskGroupEntity taskGroup = taskGroupRepository.findById(taskGroupId);
        if (taskGroup == null) {
            throw new RuntimeValidationException(RuntimeErrorCodes.TASK_GROUP_NOT_FOUND,
                    "task group does not exist");
        }
        if (!"ACTIVE".equals(taskGroup.getGroupStatus())) {
            throw new RuntimeValidationException(RuntimeErrorCodes.TASK_GROUP_STATUS_INVALID,
                    "task group status does not allow action: " + actionType);
        }
        if (expectedGroupVersion == null
                || taskGroup.getLockVersion() == null
                || taskGroup.getLockVersion().longValue() != expectedGroupVersion.longValue()) {
            throw new RuntimeValidationException(RuntimeErrorCodes.TASK_GROUP_CONCURRENT_MODIFIED,
                    "task group version has changed");
        }
        return taskGroup;
    }

    private void validateOperator(UserContext operator) {
        if (operator == null || operator.getUserId() == null || operator.getUserId().trim().isEmpty()) {
            throw new RuntimeValidationException(RuntimeErrorCodes.OPERATOR_REQUIRED, "operator is required");
        }
    }

    private void requireRunning(ProcessInstanceEntity instance) {
        if (!InstanceStatusEnum.RUNNING.name().equals(instance.getInstanceStatus())) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INSTANCE_STATUS_INVALID,
                    "instance is not running");
        }
    }

    private void validateTaskStatus(ProcessActiveTaskEntity task, ActionTypeEnum actionType) {
        String status = task.getTaskStatus();
        boolean allowed;
        if (ActionTypeEnum.CLAIM.equals(actionType)) {
            allowed = TaskStatusEnum.ACTIVE.name().equals(status);
        } else if (ActionTypeEnum.UNCLAIM.equals(actionType)) {
            allowed = TaskStatusEnum.CLAIMED.name().equals(status);
        } else {
            allowed = TaskStatusEnum.ACTIVE.name().equals(status)
                    || TaskStatusEnum.CLAIMED.name().equals(status);
        }
        if (!allowed) {
            throw new RuntimeValidationException(RuntimeErrorCodes.TASK_STATUS_INVALID,
                    "task status does not allow action: " + actionType);
        }
    }

    private void validateTaskVersion(ProcessActiveTaskEntity task, Long expectedTaskVersion) {
        if (expectedTaskVersion == null
                || task.getLockVersion() == null
                || task.getLockVersion().longValue() != expectedTaskVersion.longValue()) {
            throw new RuntimeValidationException(RuntimeErrorCodes.TASK_CONCURRENT_MODIFIED,
                    "task version has changed");
        }
    }
}

package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.HandleTypeEnum;
import com.flowmind.platform.api.enums.InstanceStatusEnum;
import com.flowmind.platform.api.enums.TaskStatusEnum;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;

/**
 * 运行时持久化实体到 API DTO 的集中转换器。
 *
 * @author FlowMind
 * @since 2026-07-22
 */
public final class RuntimeModelMapper {

    private RuntimeModelMapper() {
    }

    /** 将流程实例实体转换为包含变量和当前节点的 DTO。 */
    public static ProcessInstanceDTO toDto(ProcessInstanceEntity entity) {
        ProcessInstanceDTO dto = new ProcessInstanceDTO();
        dto.setInstanceId(entity.getId());
        dto.setDefinitionId(entity.getDefinitionId());
        dto.setAttachmentConfigId(entity.getAttachmentConfigId());
        dto.setProcessCode(entity.getProcessCode());
        dto.setProcessName(entity.getProcessName());
        dto.setVersion(entity.getVersion());
        dto.setInstanceTitle(entity.getInstanceTitle());
        dto.setBusinessKey(entity.getBusinessKey());
        dto.setStarterUserId(entity.getStarterUserId());
        dto.setStarterUserName(entity.getStarterUserName());
        dto.setStarterDeptId(entity.getStarterDeptId());
        dto.setInstanceStatus(toEnum(InstanceStatusEnum.class, entity.getInstanceStatus()));
        dto.setCurrentNodeCodes(RuntimeJsonCodec.readStringList(entity.getCurrentNodeCodes()));
        dto.setVariables(RuntimeJsonCodec.readObjectMap(entity.getVariablesJson()));
        dto.setStartedAt(entity.getStartedAt());
        dto.setEndedAt(entity.getEndedAt());
        return dto;
    }

    /** 将活动任务实体转换为 DTO，并由调用方补充定义图中的节点和委托人展示信息。 */
    public static TaskDTO toDto(ProcessActiveTaskEntity entity, String nodeName, String delegateFromUserName) {
        TaskDTO dto = new TaskDTO();
        dto.setTaskId(entity.getId());
        dto.setInstanceId(entity.getInstanceId());
        dto.setDefinitionId(entity.getDefinitionId());
        dto.setNodeCode(entity.getNodeCode());
        dto.setNodeName(nodeName);
        dto.setCandidateUserIds(RuntimeJsonCodec.readStringList(entity.getCandidateUserIds()));
        dto.setAssigneeUserId(entity.getAssigneeUserId());
        dto.setAssigneeUserName(entity.getAssigneeUserName());
        dto.setDelegateFromUserId(entity.getDelegateFromUserId());
        dto.setDelegateFromUserName(delegateFromUserName);
        dto.setTaskGroupId(entity.getTaskGroupId());
        dto.setBranchKey(entity.getBranchKey());
        dto.setTaskStatus(toEnum(TaskStatusEnum.class, entity.getTaskStatus()));
        dto.setTaskVersion(entity.getLockVersion());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setDueAt(entity.getDueAt());
        return dto;
    }

    /** 将历史任务实体转换为 DTO。 */
    public static HistoryTaskDTO toDto(ProcessHistoryTaskEntity entity) {
        HistoryTaskDTO dto = new HistoryTaskDTO();
        dto.setHistoryTaskId(entity.getId());
        dto.setInstanceId(entity.getInstanceId());
        dto.setOperationId(entity.getOperationId());
        dto.setActiveTaskId(entity.getActiveTaskId());
        dto.setNodeCode(entity.getNodeCode());
        dto.setTaskGroupId(entity.getTaskGroupId());
        dto.setBranchKey(entity.getBranchKey());
        dto.setAssigneeUserId(entity.getAssigneeUserId());
        dto.setAssigneeUserName(entity.getAssigneeUserName());
        dto.setDelegateFromUserId(entity.getDelegateFromUserId());
        dto.setDelegateFromUserName(entity.getDelegateFromUserName());
        dto.setHandleType(toEnum(HandleTypeEnum.class, entity.getHandleType()));
        dto.setActionType(toEnum(ActionTypeEnum.class, entity.getActionType()));
        dto.setComment(entity.getCommentText());
        dto.setVariablesSnapshot(RuntimeJsonCodec.readObjectMap(entity.getVariablesSnapshot()));
        dto.setStartedAt(entity.getStartedAt());
        dto.setCompletedAt(entity.getCompletedAt());
        return dto;
    }

    private static <T extends Enum<T>> T toEnum(Class<T> enumType, String value) {
        return value == null || value.trim().isEmpty() ? null : Enum.valueOf(enumType, value);
    }
}

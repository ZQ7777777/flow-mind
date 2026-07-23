package com.flowmind.platform.core.query;

import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.HandleTypeEnum;
import com.flowmind.platform.api.enums.InstanceStatusEnum;
import com.flowmind.platform.api.enums.TaskStatusEnum;
import com.flowmind.platform.core.runtime.RuntimeJsonCodec;
import com.flowmind.platform.persistence.entity.HistoryTaskQueryEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.entity.TaskQueryEntity;
import org.springframework.stereotype.Component;

/**
 * M3 查询读模型到 API DTO 的组装器。
 *
 * @author FlowMind
 * @since 2026-07-23
 */
@Component
public class RuntimeQueryAssembler {

    /** 将活动任务查询行转换为任务 DTO。 */
    public TaskDTO toTaskDTO(TaskQueryEntity entity) {
        TaskDTO dto = new TaskDTO();
        dto.setTaskId(entity.getTaskId());
        dto.setInstanceId(entity.getInstanceId());
        dto.setDefinitionId(entity.getDefinitionId());
        dto.setProcessCode(entity.getProcessCode());
        dto.setProcessName(entity.getProcessName());
        dto.setInstanceTitle(entity.getInstanceTitle());
        dto.setStarterUserId(entity.getStarterUserId());
        dto.setStarterUserName(entity.getStarterUserName());
        dto.setNodeCode(entity.getNodeCode());
        dto.setNodeName(entity.getNodeName());
        dto.setCandidateUserIds(RuntimeJsonCodec.readStringList(entity.getCandidateUserIds()));
        dto.setAssigneeUserId(entity.getAssigneeUserId());
        dto.setAssigneeUserName(entity.getAssigneeUserName());
        dto.setDelegateFromUserId(entity.getDelegateFromUserId());
        dto.setDelegateFromUserName(entity.getDelegateFromUserName());
        dto.setTaskGroupId(entity.getTaskGroupId());
        dto.setBranchKey(entity.getBranchKey());
        dto.setTaskStatus(toEnum(TaskStatusEnum.class, entity.getTaskStatus()));
        dto.setTaskVersion(entity.getLockVersion());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setDueAt(entity.getDueAt());
        return dto;
    }

    /** 将历史任务查询行转换为历史任务 DTO。 */
    public HistoryTaskDTO toHistoryTaskDTO(HistoryTaskQueryEntity entity) {
        HistoryTaskDTO dto = new HistoryTaskDTO();
        dto.setHistoryTaskId(entity.getHistoryTaskId());
        dto.setInstanceId(entity.getInstanceId());
        dto.setProcessCode(entity.getProcessCode());
        dto.setProcessName(entity.getProcessName());
        dto.setInstanceTitle(entity.getInstanceTitle());
        dto.setStarterUserId(entity.getStarterUserId());
        dto.setStarterUserName(entity.getStarterUserName());
        dto.setOperationId(entity.getOperationId());
        dto.setActiveTaskId(entity.getActiveTaskId());
        dto.setNodeCode(entity.getNodeCode());
        dto.setNodeName(entity.getNodeName());
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

    /** 将流程实例实体转换为实例 DTO。 */
    public ProcessInstanceDTO toProcessInstanceDTO(ProcessInstanceEntity entity) {
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

    private <T extends Enum<T>> T toEnum(Class<T> enumType, String value) {
        return value == null || value.trim().isEmpty() ? null : Enum.valueOf(enumType, value);
    }
}

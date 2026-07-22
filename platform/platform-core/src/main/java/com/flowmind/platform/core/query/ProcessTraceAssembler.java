package com.flowmind.platform.core.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.ProcessCommentDTO;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.HandleTypeEnum;
import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 历史轨迹和审批意见 DTO 组装器。
 */
@Component
public class ProcessTraceAssembler {

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public HistoryTaskDTO toHistoryTaskDTO(ProcessHistoryTaskEntity entity) {
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
        dto.setHandleType(parseHandleType(entity.getHandleType()));
        dto.setActionType(parseActionType(entity.getActionType()));
        dto.setComment(entity.getCommentText());
        dto.setVariablesSnapshot(parseVariables(entity.getVariablesSnapshot()));
        dto.setStartedAt(entity.getStartedAt());
        dto.setCompletedAt(entity.getCompletedAt());
        return dto;
    }

    public ProcessCommentDTO toCommentDTO(ProcessHistoryTaskEntity entity) {
        if (entity.getCommentText() == null || entity.getCommentText().trim().isEmpty()) {
            return null;
        }
        ProcessCommentDTO dto = new ProcessCommentDTO();
        dto.setCommentId(entity.getId());
        dto.setInstanceId(entity.getInstanceId());
        dto.setTaskId(entity.getActiveTaskId());
        dto.setNodeCode(entity.getNodeCode());
        dto.setOperatorUserId(entity.getAssigneeUserId());
        dto.setOperatorUserName(entity.getAssigneeUserName());
        dto.setComment(entity.getCommentText());
        dto.setCreatedAt(entity.getCompletedAt());
        return dto;
    }

    private HandleTypeEnum parseHandleType(String value) {
        return value == null ? null : HandleTypeEnum.valueOf(value);
    }

    private ActionTypeEnum parseActionType(String value) {
        return value == null ? null : ActionTypeEnum.valueOf(value);
    }

    private Map<String, Object> parseVariables(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("history variables snapshot json is invalid", ex);
        }
    }
}

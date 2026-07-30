package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.DirectSendContextDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.core.definition.TaskActionRuleConfigReader;
import com.flowmind.platform.core.definition.TaskActionRuleConfigReader.TaskActionRules;
import com.flowmind.platform.core.validation.DefinitionGraphIndex;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import com.flowmind.platform.persistence.repository.ProcessHistoryTaskRepository;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 从可信驳回历史解析当前返工任务的直送目标。
 *
 * @author FlowMind
 * @since 2026-07-30
 */
public class DirectSendContextResolver {

    private final ProcessHistoryTaskRepository historyRepository;
    private final TaskActionRuleConfigReader ruleReader = new TaskActionRuleConfigReader();

    public DirectSendContextResolver(ProcessHistoryTaskRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    /**
     * 解析可执行直送的内部上下文；不满足条件时返回空值。
     */
    public Resolution resolve(ProcessActiveTaskEntity task, ProcessDefinitionDetailDTO definition) {
        if (task == null || definition == null || !isOpen(task)
                || !isBlank(task.getTaskGroupId()) || !isBlank(task.getBranchKey())) {
            return null;
        }
        ProcessNodeDTO currentNode = DefinitionGraphIndex.from(definition).getNodesByCode().get(task.getNodeCode());
        if (!isUserTask(currentNode) || !directSendEnabled(currentNode)) {
            return null;
        }
        for (ProcessHistoryTaskEntity history : historyRepository.findLatestByInstanceAndActions(
                task.getInstanceId(), ActionTypeEnum.REJECT.name())) {
            Map<String, Object> metadata = readMetadata(history.getExtraJson());
            if (!schemaVersionOne(metadata) || !stringList(metadata.get("createdTaskIds")).contains(task.getId())) {
                continue;
            }
            String sourceNodeCode = text(metadata.get("sourceNodeCode"));
            ProcessNodeDTO targetNode = DefinitionGraphIndex.from(definition).getNodesByCode().get(sourceNodeCode);
            if (isUserTask(targetNode)) {
                return new Resolution(history, targetNode);
            }
        }
        return null;
    }

    /**
     * 生成对调用方安全的只读上下文。
     */
    public DirectSendContextDTO toDto(String taskId, Resolution resolution) {
        DirectSendContextDTO dto = new DirectSendContextDTO();
        dto.setTaskId(taskId);
        dto.setAllowed(resolution != null);
        if (resolution != null) {
            dto.setTargetNodeCode(resolution.getTargetNode().getNodeCode());
            dto.setTargetNodeName(resolution.getTargetNode().getNodeName());
        }
        return dto;
    }

    private boolean directSendEnabled(ProcessNodeDTO node) {
        try {
            TaskActionRules rules = ruleReader.read(node.getListenerConfig());
            return rules.isDirectSendEnabled()
                    && TaskActionRuleConfigReader.TARGET_MODE_REJECT_SOURCE.equals(
                    rules.getDirectSendTargetMode());
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private Map<String, Object> readMetadata(String json) {
        try {
            return RuntimeJsonCodec.readObjectMap(json);
        } catch (IllegalArgumentException ex) {
            return Collections.emptyMap();
        }
    }

    private boolean schemaVersionOne(Map<String, Object> metadata) {
        Object value = metadata.get("schemaVersion");
        return value instanceof Number && ((Number) value).intValue() == 1;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?>)) {
            return Collections.emptyList();
        }
        java.util.ArrayList<String> result = new java.util.ArrayList<String>();
        for (Object item : (List<?>) value) {
            if (item instanceof String) {
                result.add((String) item);
            }
        }
        return result;
    }

    private String text(Object value) {
        return value instanceof String && !isBlank((String) value) ? (String) value : null;
    }

    private boolean isUserTask(ProcessNodeDTO node) {
        return node != null && NodeTypeEnum.USER_TASK.equals(node.getNodeType());
    }

    private boolean isOpen(ProcessActiveTaskEntity task) {
        return "ACTIVE".equals(task.getTaskStatus()) || "CLAIMED".equals(task.getTaskStatus());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** 已验证的驳回历史和直送目标。 */
    public static final class Resolution {
        private final ProcessHistoryTaskEntity rejectHistory;
        private final ProcessNodeDTO targetNode;

        Resolution(ProcessHistoryTaskEntity rejectHistory, ProcessNodeDTO targetNode) {
            this.rejectHistory = rejectHistory;
            this.targetNode = targetNode;
        }

        public ProcessHistoryTaskEntity getRejectHistory() {
            return rejectHistory;
        }

        public ProcessNodeDTO getTargetNode() {
            return targetNode;
        }
    }
}

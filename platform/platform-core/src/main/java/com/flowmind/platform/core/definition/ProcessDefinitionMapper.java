package com.flowmind.platform.core.definition;

import com.flowmind.platform.api.dto.ProcessAttachmentTemplateDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessFormFieldDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.enums.ActivationStatusEnum;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.DefinitionStatusEnum;
import com.flowmind.platform.api.enums.GrayStatusEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.persistence.entity.ProcessDefinitionEntity;
import com.flowmind.platform.persistence.entity.ProcessEdgeEntity;
import com.flowmind.platform.persistence.entity.ProcessNodeEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 流程定义管理 DTO 与持久化实体转换器。
 *
 * @author Yuxin Xu
 * @since 2026-07-17
 */
public final class ProcessDefinitionMapper {

    private ProcessDefinitionMapper() {
    }

    public static ProcessDefinitionDTO toDto(ProcessDefinitionEntity entity) {
        if (entity == null) {
            return null;
        }
        ProcessDefinitionDTO dto = new ProcessDefinitionDTO();
        copyDefinitionToDto(entity, dto);
        return dto;
    }

    public static ProcessDefinitionEntity toEntity(ProcessDefinitionDTO dto) {
        if (dto == null) {
            return null;
        }
        ProcessDefinitionEntity entity = new ProcessDefinitionEntity();
        entity.setId(dto.getId());
        entity.setProcessCode(dto.getProcessCode());
        entity.setProcessName(dto.getProcessName());
        entity.setSystemCode(dto.getSystemCode());
        entity.setVersion(dto.getVersion());
        entity.setDefinitionStatus(enumName(dto.getDefinitionStatus()));
        entity.setActivationStatus(enumName(dto.getActivationStatus()));
        entity.setGrayStatus(enumName(dto.getGrayStatus()));
        entity.setGrayRuleConfig(dto.getGrayRuleConfig());
        entity.setCreatedBy(dto.getCreatedBy());
        entity.setCreatedAt(dto.getCreatedAt());
        entity.setUpdatedBy(dto.getUpdatedBy());
        entity.setUpdatedAt(dto.getUpdatedAt());
        return entity;
    }

    /**
     * 组装流程定义详情 DTO。
     *
     * @param definition        定义主表实体，决定详情对象的基础信息
     * @param nodes             定义下的节点实体列表，按仓储排序结果原样映射
     * @param edges             定义下的连线实体列表，按仓储排序结果原样映射
     * @param formFields        定义下的表单字段 DTO 列表，由表单字段管理组件提供
     * @param attachmentTemplates 定义下的附件配置与模板合并视图，由附件配置管理组件提供
     * @return 对外查询使用的流程定义详情；definition 为空时返回 null
     */
    public static ProcessDefinitionDetailDTO toDetailDto(ProcessDefinitionEntity definition,
                                                         List<ProcessNodeEntity> nodes,
                                                         List<ProcessEdgeEntity> edges,
                                                         List<ProcessFormFieldDTO> formFields,
                                                         List<ProcessAttachmentTemplateDTO> attachmentTemplates) {
        if (definition == null) {
            return null;
        }
        ProcessDefinitionDetailDTO detail = new ProcessDefinitionDetailDTO();
        copyDefinitionToDto(definition, detail);
        detail.setNodes(toNodeDtos(nodes));
        detail.setEdges(toEdgeDtos(edges));
        detail.setFormFields(formFields == null
                ? new ArrayList<ProcessFormFieldDTO>() : new ArrayList<ProcessFormFieldDTO>(formFields));
        detail.setAttachmentTemplates(attachmentTemplates == null
                ? new ArrayList<ProcessAttachmentTemplateDTO>()
                : new ArrayList<ProcessAttachmentTemplateDTO>(attachmentTemplates));
        return detail;
    }

    public static ProcessNodeDTO toNodeDto(ProcessNodeEntity entity) {
        if (entity == null) {
            return null;
        }
        ProcessNodeDTO dto = new ProcessNodeDTO();
        dto.setId(entity.getId());
        dto.setDefinitionId(entity.getDefinitionId());
        dto.setNodeCode(entity.getNodeCode());
        dto.setNodeName(entity.getNodeName());
        dto.setNodeType(enumValue(NodeTypeEnum.class, entity.getNodeType()));
        dto.setPairedGatewayCode(entity.getPairedGatewayCode());
        dto.setApproverRuleType(enumValue(ApproverRuleTypeEnum.class, entity.getApproverRuleType()));
        dto.setApproverRuleConfig(entity.getApproverRuleConfig());
        dto.setMultiInstanceMode(enumValue(MultiInstanceModeEnum.class, entity.getMultiInstanceMode()));
        dto.setListenerConfig(entity.getListenerConfig());
        dto.setTimeoutConfig(entity.getTimeoutConfig());
        dto.setReminderConfig(entity.getReminderConfig());
        dto.setPositionX(entity.getPositionX());
        dto.setPositionY(entity.getPositionY());
        dto.setSortOrder(entity.getSortOrder());
        return dto;
    }

    public static ProcessNodeEntity toNodeEntity(ProcessNodeDTO dto) {
        if (dto == null) {
            return null;
        }
        ProcessNodeEntity entity = new ProcessNodeEntity();
        entity.setId(dto.getId());
        entity.setDefinitionId(dto.getDefinitionId());
        entity.setNodeCode(dto.getNodeCode());
        entity.setNodeName(dto.getNodeName());
        entity.setNodeType(enumName(dto.getNodeType()));
        entity.setPairedGatewayCode(dto.getPairedGatewayCode());
        entity.setApproverRuleType(enumName(dto.getApproverRuleType()));
        entity.setApproverRuleConfig(dto.getApproverRuleConfig());
        entity.setMultiInstanceMode(enumName(dto.getMultiInstanceMode()));
        entity.setListenerConfig(dto.getListenerConfig());
        entity.setTimeoutConfig(dto.getTimeoutConfig());
        entity.setReminderConfig(dto.getReminderConfig());
        entity.setPositionX(dto.getPositionX());
        entity.setPositionY(dto.getPositionY());
        entity.setSortOrder(dto.getSortOrder());
        return entity;
    }

    public static ProcessEdgeDTO toEdgeDto(ProcessEdgeEntity entity) {
        if (entity == null) {
            return null;
        }
        ProcessEdgeDTO dto = new ProcessEdgeDTO();
        dto.setId(entity.getId());
        dto.setDefinitionId(entity.getDefinitionId());
        dto.setEdgeCode(entity.getEdgeCode());
        dto.setSourceNodeCode(entity.getSourceNodeCode());
        dto.setTargetNodeCode(entity.getTargetNodeCode());
        dto.setConditionExpression(entity.getConditionExpression());
        dto.setDefaultEdge(entity.getDefaultEdge());
        dto.setSortOrder(entity.getSortOrder());
        return dto;
    }

    public static ProcessEdgeEntity toEdgeEntity(ProcessEdgeDTO dto) {
        if (dto == null) {
            return null;
        }
        ProcessEdgeEntity entity = new ProcessEdgeEntity();
        entity.setId(dto.getId());
        entity.setDefinitionId(dto.getDefinitionId());
        entity.setEdgeCode(dto.getEdgeCode());
        entity.setSourceNodeCode(dto.getSourceNodeCode());
        entity.setTargetNodeCode(dto.getTargetNodeCode());
        entity.setConditionExpression(dto.getConditionExpression());
        entity.setDefaultEdge(dto.getDefaultEdge());
        entity.setSortOrder(dto.getSortOrder());
        return entity;
    }

    public static List<ProcessNodeEntity> toNodeEntities(List<ProcessNodeDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProcessNodeEntity> entities = new ArrayList<ProcessNodeEntity>(dtos.size());
        for (ProcessNodeDTO dto : dtos) {
            entities.add(toNodeEntity(dto));
        }
        return entities;
    }

    public static List<ProcessEdgeEntity> toEdgeEntities(List<ProcessEdgeDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProcessEdgeEntity> entities = new ArrayList<ProcessEdgeEntity>(dtos.size());
        for (ProcessEdgeDTO dto : dtos) {
            entities.add(toEdgeEntity(dto));
        }
        return entities;
    }

    private static void copyDefinitionToDto(ProcessDefinitionEntity entity, ProcessDefinitionDTO dto) {
        dto.setId(entity.getId());
        dto.setProcessCode(entity.getProcessCode());
        dto.setProcessName(entity.getProcessName());
        dto.setSystemCode(entity.getSystemCode());
        dto.setVersion(entity.getVersion());
        dto.setDefinitionStatus(enumValue(DefinitionStatusEnum.class, entity.getDefinitionStatus()));
        dto.setActivationStatus(enumValue(ActivationStatusEnum.class, entity.getActivationStatus()));
        dto.setGrayStatus(enumValue(GrayStatusEnum.class, entity.getGrayStatus()));
        dto.setGrayRuleConfig(entity.getGrayRuleConfig());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setUpdatedAt(entity.getUpdatedAt());
    }

    public static List<ProcessNodeDTO> toNodeDtos(List<ProcessNodeEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return new ArrayList<ProcessNodeDTO>();
        }
        List<ProcessNodeDTO> dtos = new ArrayList<ProcessNodeDTO>(entities.size());
        for (ProcessNodeEntity entity : entities) {
            dtos.add(toNodeDto(entity));
        }
        return dtos;
    }

    public static List<ProcessEdgeDTO> toEdgeDtos(List<ProcessEdgeEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return new ArrayList<ProcessEdgeDTO>();
        }
        List<ProcessEdgeDTO> dtos = new ArrayList<ProcessEdgeDTO>(entities.size());
        for (ProcessEdgeEntity entity : entities) {
            dtos.add(toEdgeDto(entity));
        }
        return dtos;
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> enumType, String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return Enum.valueOf(enumType, value.trim());
    }

}

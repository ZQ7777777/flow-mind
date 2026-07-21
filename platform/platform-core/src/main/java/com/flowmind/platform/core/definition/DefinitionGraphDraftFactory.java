package com.flowmind.platform.core.definition;

import com.flowmind.platform.api.dto.ProcessAttachmentConfigDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessFormFieldDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.persistence.entity.ProcessDefinitionAttachmentConfigEntity;
import com.flowmind.platform.persistence.entity.ProcessEdgeEntity;
import com.flowmind.platform.persistence.entity.ProcessFormFieldEntity;
import com.flowmind.platform.persistence.entity.ProcessNodeEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 流程定义图草稿工厂，统一维护图子对象的默认值、定义绑定和复制规则。
 *
 * @author Yuxin Xu
 * @since 2026-07-17
 */
final class DefinitionGraphDraftFactory {

    /**
     * 草稿态附件配置的默认业务配置号后缀；同一定义内未显式传入 attachmentConfigId 时使用。
     */
    private static final String DEFAULT_ATTACHMENT_CONFIG_ID_SUFFIX = ":draft-attachment-config";

    private final Supplier<String> idSupplier;

    DefinitionGraphDraftFactory() {
        this(new Supplier<String>() {
            @Override
            public String get() {
                return UUID.randomUUID().toString();
            }
        });
    }

    /**
     * 测试用构造器，允许注入稳定 ID 生成器。
     *
     * @param idSupplier 新增或复制图子对象时使用的 ID 生成器
     */
    DefinitionGraphDraftFactory(Supplier<String> idSupplier) {
        if (idSupplier == null) {
            throw new IllegalArgumentException("idSupplier must not be null");
        }
        this.idSupplier = idSupplier;
    }

    /**
     * 归一化流程节点草稿。
     *
     * @param definitionId 当前被保存的流程定义 ID，所有节点都会重新绑定到该定义
     * @param nodes        请求中的节点列表，不能为空，列表顺序用于补齐默认 sortOrder
     * @return 已补齐 ID、definitionId、默认会签模式和排序号的新节点列表
     */
    List<ProcessNodeDTO> normalizeNodes(String definitionId, List<ProcessNodeDTO> nodes) {
        List<ProcessNodeDTO> normalized = new ArrayList<ProcessNodeDTO>(nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            ProcessNodeDTO source = nodes.get(i);
            if (source == null) {
                throw new IllegalArgumentException("node must not be null");
            }
            ProcessNodeDTO target = new ProcessNodeDTO();
            target.setId(hasText(source.getId()) ? source.getId() : newId());
            target.setDefinitionId(definitionId);
            target.setNodeCode(trimToNull(source.getNodeCode()));
            target.setNodeName(trimToNull(source.getNodeName()));
            target.setNodeType(source.getNodeType());
            target.setPairedGatewayCode(trimToNull(source.getPairedGatewayCode()));
            target.setApproverRuleType(source.getApproverRuleType());
            target.setApproverRuleConfig(source.getApproverRuleConfig());
            target.setMultiInstanceMode(source.getMultiInstanceMode() == null
                    ? MultiInstanceModeEnum.SINGLE : source.getMultiInstanceMode());
            target.setListenerConfig(source.getListenerConfig());
            target.setTimeoutConfig(source.getTimeoutConfig());
            target.setReminderConfig(source.getReminderConfig());
            target.setPositionX(source.getPositionX());
            target.setPositionY(source.getPositionY());
            target.setSortOrder(defaultSortOrder(source.getSortOrder(), i));
            normalized.add(target);
        }
        return normalized;
    }

    /**
     * 归一化流程连线草稿。
     *
     * @param definitionId 当前被保存的流程定义 ID，所有连线都会重新绑定到该定义
     * @param edges        请求中的连线列表，不能为空，列表顺序用于补齐默认 sortOrder
     * @return 已补齐 ID、definitionId、默认连线标记和排序号的新连线列表
     */
    List<ProcessEdgeDTO> normalizeEdges(String definitionId, List<ProcessEdgeDTO> edges) {
        List<ProcessEdgeDTO> normalized = new ArrayList<ProcessEdgeDTO>(edges.size());
        for (int i = 0; i < edges.size(); i++) {
            ProcessEdgeDTO source = edges.get(i);
            if (source == null) {
                throw new IllegalArgumentException("edge must not be null");
            }
            ProcessEdgeDTO target = new ProcessEdgeDTO();
            target.setId(hasText(source.getId()) ? source.getId() : newId());
            target.setDefinitionId(definitionId);
            target.setEdgeCode(trimToNull(source.getEdgeCode()));
            target.setSourceNodeCode(trimToNull(source.getSourceNodeCode()));
            target.setTargetNodeCode(trimToNull(source.getTargetNodeCode()));
            target.setConditionExpression(source.getConditionExpression());
            target.setDefaultEdge(Boolean.valueOf(Boolean.TRUE.equals(source.getDefaultEdge())));
            target.setSortOrder(defaultSortOrder(source.getSortOrder(), i));
            normalized.add(target);
        }
        return normalized;
    }

    /**
     * 归一化表单字段草稿。
     *
     * @param definitionId 当前被保存的流程定义 ID，所有字段都会重新绑定到该定义
     * @param formFields   请求中的表单字段列表；为空表示该定义没有表单字段
     * @return 已补齐 ID、definitionId、required 默认值和排序号的新字段列表
     */
    List<ProcessFormFieldDTO> normalizeFormFields(String definitionId, List<ProcessFormFieldDTO> formFields) {
        if (formFields == null || formFields.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProcessFormFieldDTO> normalized = new ArrayList<ProcessFormFieldDTO>(formFields.size());
        for (int i = 0; i < formFields.size(); i++) {
            ProcessFormFieldDTO source = formFields.get(i);
            if (source == null) {
                throw new IllegalArgumentException("formField must not be null");
            }
            ProcessFormFieldDTO target = new ProcessFormFieldDTO();
            target.setId(hasText(source.getId()) ? source.getId() : newId());
            target.setDefinitionId(definitionId);
            target.setFieldCode(trimToNull(source.getFieldCode()));
            target.setFieldName(trimToNull(source.getFieldName()));
            target.setFieldType(trimToNull(source.getFieldType()));
            target.setControlType(trimToNull(source.getControlType()));
            target.setRequired(Boolean.valueOf(Boolean.TRUE.equals(source.getRequired())));
            target.setValidationRule(source.getValidationRule());
            target.setDefaultValue(source.getDefaultValue());
            target.setSortOrder(defaultSortOrder(source.getSortOrder(), i));
            normalized.add(target);
        }
        return normalized;
    }

    /**
     * 归一化附件配置草稿。
     *
     * @param definitionId 当前被保存的流程定义 ID，作为附件配置业务号默认值的一部分
     * @param configs      请求中的附件配置列表；为空表示该定义没有附件约束
     * @return 已补齐 configId、attachmentConfigId、definitionId、数量下限和排序号的新附件配置列表
     */
    List<ProcessAttachmentConfigDTO> normalizeAttachmentConfigs(String definitionId,
                                                                List<ProcessAttachmentConfigDTO> configs) {
        if (configs == null || configs.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProcessAttachmentConfigDTO> normalized = new ArrayList<ProcessAttachmentConfigDTO>(configs.size());
        for (int i = 0; i < configs.size(); i++) {
            ProcessAttachmentConfigDTO source = configs.get(i);
            if (source == null) {
                throw new IllegalArgumentException("attachmentConfig must not be null");
            }
            ProcessAttachmentConfigDTO target = new ProcessAttachmentConfigDTO();
            target.setConfigId(hasText(source.getConfigId()) ? source.getConfigId() : newId());
            target.setAttachmentConfigId(hasText(source.getAttachmentConfigId())
                    ? source.getAttachmentConfigId() : draftAttachmentConfigId(definitionId));
            target.setDefinitionId(definitionId);
            target.setAttachmentTemplateId(trimToNull(source.getAttachmentTemplateId()));
            target.setAttachmentCode(trimToNull(source.getAttachmentCode()));
            target.setRequired(Boolean.valueOf(Boolean.TRUE.equals(source.getRequired())));
            target.setMinCount(source.getMinCount() == null
                    ? Integer.valueOf(Boolean.TRUE.equals(source.getRequired()) ? 1 : 0)
                    : source.getMinCount());
            target.setMaxCount(source.getMaxCount());
            target.setApplicableNodeCodes(source.getApplicableNodeCodes());
            target.setSortOrder(defaultSortOrder(source.getSortOrder(), i));
            normalized.add(target);
        }
        return normalized;
    }

    /**
     * 将已归一化的附件配置转换为草稿态持久化实体。
     *
     * @param attachmentConfigs 归一化后的附件配置 DTO 列表
     * @param operatorUserId    当前操作人 ID，用于 createdBy/updatedBy 审计字段
     * @param now               当前业务时间，用于 createdAt/updatedAt 审计字段
     * @return 可直接批量写入 process_definition_attachment_config 的实体列表
     */
    List<ProcessDefinitionAttachmentConfigEntity> toDraftAttachmentConfigEntities(
            List<ProcessAttachmentConfigDTO> attachmentConfigs,
            String operatorUserId,
            LocalDateTime now) {
        List<ProcessDefinitionAttachmentConfigEntity> entities =
                ProcessDefinitionMapper.toAttachmentConfigEntities(attachmentConfigs);
        for (ProcessDefinitionAttachmentConfigEntity entity : entities) {
            entity.setConfigStatus("DRAFT");
            entity.setCreatedBy(operatorUserId);
            entity.setCreatedAt(now);
            entity.setUpdatedBy(operatorUserId);
            entity.setUpdatedAt(now);
        }
        return entities;
    }

    /**
     * 复制节点实体到新的流程定义。
     *
     * @param sourceNodes        源定义下已持久化的节点实体
     * @param targetDefinitionId 新定义 ID，复制结果都会绑定到该定义并生成新主键
     * @return 可批量插入到目标定义的节点实体
     */
    List<ProcessNodeEntity> copyNodeEntities(List<ProcessNodeEntity> sourceNodes, String targetDefinitionId) {
        if (sourceNodes == null || sourceNodes.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProcessNodeEntity> copied = new ArrayList<ProcessNodeEntity>(sourceNodes.size());
        for (ProcessNodeEntity source : sourceNodes) {
            ProcessNodeEntity target = new ProcessNodeEntity();
            target.setId(newId());
            target.setDefinitionId(targetDefinitionId);
            target.setNodeCode(source.getNodeCode());
            target.setNodeName(source.getNodeName());
            target.setNodeType(source.getNodeType());
            target.setPairedGatewayCode(source.getPairedGatewayCode());
            target.setApproverRuleType(source.getApproverRuleType());
            target.setApproverRuleConfig(source.getApproverRuleConfig());
            target.setMultiInstanceMode(source.getMultiInstanceMode());
            target.setListenerConfig(source.getListenerConfig());
            target.setTimeoutConfig(source.getTimeoutConfig());
            target.setReminderConfig(source.getReminderConfig());
            target.setPositionX(source.getPositionX());
            target.setPositionY(source.getPositionY());
            target.setSortOrder(source.getSortOrder());
            copied.add(target);
        }
        return copied;
    }

    /**
     * 复制连线实体到新的流程定义。
     *
     * @param sourceEdges        源定义下已持久化的连线实体
     * @param targetDefinitionId 新定义 ID，复制结果都会绑定到该定义并生成新主键
     * @return 可批量插入到目标定义的连线实体
     */
    List<ProcessEdgeEntity> copyEdgeEntities(List<ProcessEdgeEntity> sourceEdges, String targetDefinitionId) {
        if (sourceEdges == null || sourceEdges.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProcessEdgeEntity> copied = new ArrayList<ProcessEdgeEntity>(sourceEdges.size());
        for (ProcessEdgeEntity source : sourceEdges) {
            ProcessEdgeEntity target = new ProcessEdgeEntity();
            target.setId(newId());
            target.setDefinitionId(targetDefinitionId);
            target.setEdgeCode(source.getEdgeCode());
            target.setSourceNodeCode(source.getSourceNodeCode());
            target.setTargetNodeCode(source.getTargetNodeCode());
            target.setConditionExpression(source.getConditionExpression());
            target.setDefaultEdge(source.getDefaultEdge());
            target.setSortOrder(source.getSortOrder());
            copied.add(target);
        }
        return copied;
    }

    /**
     * 复制表单字段实体到新的流程定义。
     *
     * @param sourceFormFields   源定义下已持久化的表单字段实体
     * @param targetDefinitionId 新定义 ID，复制结果都会绑定到该定义并生成新主键
     * @return 可批量插入到目标定义的表单字段实体
     */
    List<ProcessFormFieldEntity> copyFormFieldEntities(List<ProcessFormFieldEntity> sourceFormFields,
                                                       String targetDefinitionId) {
        if (sourceFormFields == null || sourceFormFields.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProcessFormFieldEntity> copied = new ArrayList<ProcessFormFieldEntity>(sourceFormFields.size());
        for (ProcessFormFieldEntity source : sourceFormFields) {
            ProcessFormFieldEntity target = new ProcessFormFieldEntity();
            target.setId(newId());
            target.setDefinitionId(targetDefinitionId);
            target.setFieldCode(source.getFieldCode());
            target.setFieldName(source.getFieldName());
            target.setFieldType(source.getFieldType());
            target.setControlType(source.getControlType());
            target.setRequired(source.getRequired());
            target.setValidationRule(source.getValidationRule());
            target.setDefaultValue(source.getDefaultValue());
            target.setSortOrder(source.getSortOrder());
            copied.add(target);
        }
        return copied;
    }

    /**
     * 复制附件配置实体到新的流程定义，并重置为草稿态。
     *
     * @param sourceAttachmentConfigs 源定义下已持久化的附件配置实体
     * @param targetDefinitionId      新定义 ID，复制结果都会绑定到该定义并生成新主键
     * @param operatorUserId          当前操作人 ID，用于重置 createdBy/updatedBy 审计字段
     * @param now                     当前业务时间，用于重置 createdAt/updatedAt 审计字段
     * @return 可批量插入到目标定义的附件配置实体
     */
    List<ProcessDefinitionAttachmentConfigEntity> copyAttachmentConfigEntities(
            List<ProcessDefinitionAttachmentConfigEntity> sourceAttachmentConfigs,
            String targetDefinitionId,
            String operatorUserId,
            LocalDateTime now) {
        if (sourceAttachmentConfigs == null || sourceAttachmentConfigs.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProcessDefinitionAttachmentConfigEntity> copied =
                new ArrayList<ProcessDefinitionAttachmentConfigEntity>(sourceAttachmentConfigs.size());
        for (ProcessDefinitionAttachmentConfigEntity source : sourceAttachmentConfigs) {
            ProcessDefinitionAttachmentConfigEntity target = new ProcessDefinitionAttachmentConfigEntity();
            target.setId(newId());
            target.setAttachmentConfigId(draftAttachmentConfigId(targetDefinitionId));
            target.setDefinitionId(targetDefinitionId);
            target.setConfigStatus("DRAFT");
            target.setActivatedAt(null);
            target.setAttachmentTemplateId(source.getAttachmentTemplateId());
            target.setAttachmentCode(source.getAttachmentCode());
            target.setRequired(source.getRequired());
            target.setMinCount(source.getMinCount());
            target.setMaxCount(source.getMaxCount());
            target.setApplicableNodeCodes(source.getApplicableNodeCodes());
            target.setSortOrder(source.getSortOrder());
            target.setCreatedBy(operatorUserId);
            target.setCreatedAt(now);
            target.setUpdatedBy(operatorUserId);
            target.setUpdatedAt(now);
            copied.add(target);
        }
        return copied;
    }

    /**
     * 生成草稿态附件配置业务号。
     *
     * @param definitionId 流程定义 ID，用于保证不同定义下的默认附件配置业务号不同
     * @return 默认附件配置业务号
     */
    private String draftAttachmentConfigId(String definitionId) {
        return definitionId + DEFAULT_ATTACHMENT_CONFIG_ID_SUFFIX;
    }

    /**
     * 计算默认排序号。
     *
     * @param sortOrder 请求显式传入的排序号；非空时原样保留
     * @param index     当前对象在请求列表中的位置，从 0 开始，用于生成 10、20、30 这样的默认排序号
     * @return 可持久化的排序号
     */
    private Integer defaultSortOrder(Integer sortOrder, int index) {
        return sortOrder == null ? Integer.valueOf((index + 1) * 10) : sortOrder;
    }

    private String newId() {
        return idSupplier.get();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}

package com.flowmind.platform.runtime.definition.port;

import com.flowmind.platform.api.dto.ProcessAttachmentTemplateDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessFormFieldDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.enums.ActivationStatusEnum;
import com.flowmind.platform.api.enums.DefinitionStatusEnum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 运行时定义读取端口返回的不可变输入。
 *
 * <p>该类型复用已冻结的正式 DTO 和枚举，不复制 A/C 的公共模型。构造和读取时均执行
 * 防御性复制，避免管理端 DTO 或调用方修改污染后续解析、校验和缓存。</p>
 *
 * @author FlowMind
 * @since 2026-07-17
 */
public final class RuntimeDefinitionInput {

    /**
     * 流程定义 ID。
     */
    private final String definitionId;
    /**
     * 流程编码。
     */
    private final String processCode;
    /**
     * 流程定义版本号。
     */
    private final Integer version;
    /**
     * 定义状态，如 DRAFT、PUBLISHED、ARCHIVED。
     */
    private final DefinitionStatusEnum definitionStatus;
    /**
     * 激活状态，如 INACTIVE、ACTIVE。
     */
    private final ActivationStatusEnum activationStatus;
    /**
     * 节点不可修改快照，保留节点配置、坐标和顺序。
     */
    private final List<ProcessNodeDTO> nodes;
    /**
     * 连线不可修改快照，保留条件、默认出线和顺序。
     */
    private final List<ProcessEdgeDTO> edges;
    /**
     * 表单字段不可修改快照。
     */
    private final List<ProcessFormFieldDTO> formFields;
    /**
     * 附件模板与定义附件配置不可修改快照。
     */
    private final List<ProcessAttachmentTemplateDTO> attachmentTemplates;

    private RuntimeDefinitionInput(ProcessDefinitionDetailDTO definition) {
        this.definitionId = definition.getId();
        this.processCode = definition.getProcessCode();
        this.version = definition.getVersion();
        this.definitionStatus = definition.getDefinitionStatus();
        this.activationStatus = definition.getActivationStatus();
        this.nodes = immutableNodes(definition.getNodes());
        this.edges = immutableEdges(definition.getEdges());
        this.formFields = immutableFormFields(definition.getFormFields());
        this.attachmentTemplates = immutableAttachmentTemplates(definition.getAttachmentTemplates());
    }

    /**
     * 从正式流程定义详情创建不可变运行时输入。
     *
     * @param definition 正式流程定义详情
     * @return 不可变运行时输入
     */
    public static RuntimeDefinitionInput from(ProcessDefinitionDetailDTO definition) {
        return new RuntimeDefinitionInput(Objects.requireNonNull(definition, "definition"));
    }

    /**
     * 获取流程定义 ID。
     *
     * @return 流程定义 ID
     */
    public String getDefinitionId() {
        return definitionId;
    }

    /**
     * 获取流程编码。
     *
     * @return 流程编码
     */
    public String getProcessCode() {
        return processCode;
    }

    /**
     * 获取流程定义版本号。
     *
     * @return 流程定义版本号
     */
    public Integer getVersion() {
        return version;
    }

    /**
     * 获取定义发布状态。
     *
     * @return 定义发布状态，如 DRAFT、PUBLISHED、ARCHIVED
     */
    public DefinitionStatusEnum getDefinitionStatus() {
        return definitionStatus;
    }

    /**
     * 获取定义激活状态。
     *
     * @return 定义激活状态，如 INACTIVE、ACTIVE
     */
    public ActivationStatusEnum getActivationStatus() {
        return activationStatus;
    }

    /**
     * 获取节点列表的不可修改副本。
     *
     * @return 包含节点配置、坐标和顺序的节点列表
     */
    public List<ProcessNodeDTO> getNodes() {
        return immutableNodes(nodes);
    }

    /**
     * 获取连线列表的不可修改副本。
     *
     * @return 包含条件、默认出线和顺序的连线列表
     */
    public List<ProcessEdgeDTO> getEdges() {
        return immutableEdges(edges);
    }

    /**
     * 获取表单字段列表的不可修改副本。
     *
     * @return 表单字段列表
     */
    public List<ProcessFormFieldDTO> getFormFields() {
        return immutableFormFields(formFields);
    }

    /**
     * 获取附件模板与定义附件配置列表的不可修改副本。
     *
     * @return 包含配置组、生效状态、适用节点和模板约束的附件配置列表
     */
    public List<ProcessAttachmentTemplateDTO> getAttachmentTemplates() {
        return immutableAttachmentTemplates(attachmentTemplates);
    }

    private static List<ProcessNodeDTO> immutableNodes(List<ProcessNodeDTO> source) {
        List<ProcessNodeDTO> copies = new ArrayList<ProcessNodeDTO>();
        if (source != null) {
            for (ProcessNodeDTO node : source) {
                copies.add(copyNode(Objects.requireNonNull(node,
                        "definition.nodes must not contain null")));
            }
        }
        return Collections.unmodifiableList(copies);
    }

    private static ProcessNodeDTO copyNode(ProcessNodeDTO source) {
        ProcessNodeDTO copy = new ProcessNodeDTO();
        copy.setId(source.getId());
        copy.setDefinitionId(source.getDefinitionId());
        copy.setNodeCode(source.getNodeCode());
        copy.setNodeName(source.getNodeName());
        copy.setNodeType(source.getNodeType());
        copy.setPairedGatewayCode(source.getPairedGatewayCode());
        copy.setApproverRuleType(source.getApproverRuleType());
        copy.setApproverRuleConfig(source.getApproverRuleConfig());
        copy.setMultiInstanceMode(source.getMultiInstanceMode());
        copy.setListenerConfig(source.getListenerConfig());
        copy.setTimeoutConfig(source.getTimeoutConfig());
        copy.setReminderConfig(source.getReminderConfig());
        copy.setPositionX(source.getPositionX());
        copy.setPositionY(source.getPositionY());
        copy.setSortOrder(source.getSortOrder());
        return copy;
    }

    private static List<ProcessEdgeDTO> immutableEdges(List<ProcessEdgeDTO> source) {
        List<ProcessEdgeDTO> copies = new ArrayList<ProcessEdgeDTO>();
        if (source != null) {
            for (ProcessEdgeDTO edge : source) {
                copies.add(copyEdge(Objects.requireNonNull(edge,
                        "definition.edges must not contain null")));
            }
        }
        return Collections.unmodifiableList(copies);
    }

    private static ProcessEdgeDTO copyEdge(ProcessEdgeDTO source) {
        ProcessEdgeDTO copy = new ProcessEdgeDTO();
        copy.setId(source.getId());
        copy.setDefinitionId(source.getDefinitionId());
        copy.setEdgeCode(source.getEdgeCode());
        copy.setSourceNodeCode(source.getSourceNodeCode());
        copy.setTargetNodeCode(source.getTargetNodeCode());
        copy.setConditionExpression(source.getConditionExpression());
        copy.setDefaultEdge(source.getDefaultEdge());
        copy.setSortOrder(source.getSortOrder());
        return copy;
    }

    private static List<ProcessFormFieldDTO> immutableFormFields(List<ProcessFormFieldDTO> source) {
        List<ProcessFormFieldDTO> copies = new ArrayList<ProcessFormFieldDTO>();
        if (source != null) {
            for (ProcessFormFieldDTO field : source) {
                copies.add(copyFormField(Objects.requireNonNull(field,
                        "definition.formFields must not contain null")));
            }
        }
        return Collections.unmodifiableList(copies);
    }

    private static ProcessFormFieldDTO copyFormField(ProcessFormFieldDTO source) {
        ProcessFormFieldDTO copy = new ProcessFormFieldDTO();
        copy.setId(source.getId());
        copy.setDefinitionId(source.getDefinitionId());
        copy.setFieldCode(source.getFieldCode());
        copy.setFieldName(source.getFieldName());
        copy.setFieldType(source.getFieldType());
        copy.setControlType(source.getControlType());
        copy.setRequired(source.getRequired());
        copy.setValidationRule(source.getValidationRule());
        copy.setDefaultValue(source.getDefaultValue());
        copy.setSortOrder(source.getSortOrder());
        return copy;
    }

    private static List<ProcessAttachmentTemplateDTO> immutableAttachmentTemplates(
            List<ProcessAttachmentTemplateDTO> source) {
        List<ProcessAttachmentTemplateDTO> copies = new ArrayList<ProcessAttachmentTemplateDTO>();
        if (source != null) {
            for (ProcessAttachmentTemplateDTO attachment : source) {
                copies.add(copyAttachmentTemplate(Objects.requireNonNull(attachment,
                        "definition.attachmentTemplates must not contain null")));
            }
        }
        return Collections.unmodifiableList(copies);
    }

    private static ProcessAttachmentTemplateDTO copyAttachmentTemplate(ProcessAttachmentTemplateDTO source) {
        ProcessAttachmentTemplateDTO copy = new ProcessAttachmentTemplateDTO();
        copy.setId(source.getId());
        copy.setAttachmentConfigId(source.getAttachmentConfigId());
        copy.setDefinitionId(source.getDefinitionId());
        copy.setConfigStatus(source.getConfigStatus());
        copy.setActivatedAt(source.getActivatedAt());
        copy.setAttachmentTemplateId(source.getAttachmentTemplateId());
        copy.setAttachmentCode(source.getAttachmentCode());
        copy.setTemplateVersion(source.getTemplateVersion());
        copy.setAttachmentName(source.getAttachmentName());
        copy.setDescription(source.getDescription());
        copy.setAllowedExtensions(copyStrings(source.getAllowedExtensions()));
        copy.setMaxSizeBytes(source.getMaxSizeBytes());
        copy.setTemplateStatus(source.getTemplateStatus());
        copy.setRequired(source.getRequired());
        copy.setMinCount(source.getMinCount());
        copy.setMaxCount(source.getMaxCount());
        copy.setApplicableNodeCodes(copyStrings(source.getApplicableNodeCodes()));
        copy.setSortOrder(source.getSortOrder());
        copy.setCreatedBy(source.getCreatedBy());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedBy(source.getUpdatedBy());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private static List<String> copyStrings(List<String> source) {
        return source == null ? new ArrayList<String>() : new ArrayList<String>(source);
    }
}

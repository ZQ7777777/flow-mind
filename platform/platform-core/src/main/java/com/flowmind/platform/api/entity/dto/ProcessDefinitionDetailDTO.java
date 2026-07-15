package com.flowmind.platform.api.entity.dto;

import java.util.List;

/**
 * 流程定义详情。
 */
public class ProcessDefinitionDetailDTO extends ProcessDefinitionDTO {

    /** 流程节点列表。 */
    private List<ProcessNodeDTO> nodes;
    /** 流程连线列表。 */
    private List<ProcessEdgeDTO> edges;
    /** 表单字段列表。 */
    private List<ProcessFormFieldDTO> formFields;
    /** 附件配置列表。 */
    private List<ProcessAttachmentConfigDTO> attachmentConfigs;

    public ProcessDefinitionDetailDTO() {
    }

    public List<ProcessNodeDTO> getNodes() {
        return nodes;
    }

    public void setNodes(List<ProcessNodeDTO> nodes) {
        this.nodes = nodes;
    }

    public List<ProcessEdgeDTO> getEdges() {
        return edges;
    }

    public void setEdges(List<ProcessEdgeDTO> edges) {
        this.edges = edges;
    }

    public List<ProcessFormFieldDTO> getFormFields() {
        return formFields;
    }

    public void setFormFields(List<ProcessFormFieldDTO> formFields) {
        this.formFields = formFields;
    }

    public List<ProcessAttachmentConfigDTO> getAttachmentConfigs() {
        return attachmentConfigs;
    }

    public void setAttachmentConfigs(List<ProcessAttachmentConfigDTO> attachmentConfigs) {
        this.attachmentConfigs = attachmentConfigs;
    }
}

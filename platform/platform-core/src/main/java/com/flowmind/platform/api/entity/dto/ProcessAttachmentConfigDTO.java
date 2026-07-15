package com.flowmind.platform.api.entity.dto;

import java.util.List;

/**
 * 流程定义附件配置。
 */
public class ProcessAttachmentConfigDTO {

    /** 附件配置记录 ID。 */
    private String configId;
    /** 附件配置组 ID。 */
    private String attachmentConfigId;
    /** 流程定义 ID。 */
    private String definitionId;
    /** 附件模板版本 ID。 */
    private String attachmentTemplateId;
    /** 附件编码。 */
    private String attachmentCode;
    /** 是否必填。 */
    private Boolean required;
    /** 最少上传数量。 */
    private Integer minCount;
    /** 最多上传数量。 */
    private Integer maxCount;
    /** 适用节点编码列表。 */
    private List<String> applicableNodeCodes;
    /** 展示顺序。 */
    private Integer sortOrder;

    public ProcessAttachmentConfigDTO() {
    }

    public String getConfigId() {
        return configId;
    }

    public void setConfigId(String configId) {
        this.configId = configId;
    }

    public String getAttachmentConfigId() {
        return attachmentConfigId;
    }

    public void setAttachmentConfigId(String attachmentConfigId) {
        this.attachmentConfigId = attachmentConfigId;
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(String definitionId) {
        this.definitionId = definitionId;
    }

    public String getAttachmentTemplateId() {
        return attachmentTemplateId;
    }

    public void setAttachmentTemplateId(String attachmentTemplateId) {
        this.attachmentTemplateId = attachmentTemplateId;
    }

    public String getAttachmentCode() {
        return attachmentCode;
    }

    public void setAttachmentCode(String attachmentCode) {
        this.attachmentCode = attachmentCode;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Integer getMinCount() {
        return minCount;
    }

    public void setMinCount(Integer minCount) {
        this.minCount = minCount;
    }

    public Integer getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(Integer maxCount) {
        this.maxCount = maxCount;
    }

    public List<String> getApplicableNodeCodes() {
        return applicableNodeCodes;
    }

    public void setApplicableNodeCodes(List<String> applicableNodeCodes) {
        this.applicableNodeCodes = applicableNodeCodes;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}

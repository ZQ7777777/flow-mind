package com.flowmind.platform.api.dto;

import com.flowmind.platform.api.enums.ActivationStatusEnum;
import com.flowmind.platform.api.enums.DefinitionStatusEnum;
import com.flowmind.platform.api.enums.GrayStatusEnum;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 流程定义概要信息。
 */
public class ProcessDefinitionDTO {

    /** 流程定义 ID。 */
    private String definitionId;
    /** 流程编码。 */
    private String processCode;
    /** 流程名称。 */
    private String processName;
    /** 所属系统编码。 */
    private String systemCode;
    /** 流程定义版本号。 */
    private Integer version;
    /** 流程定义状态。 */
    private DefinitionStatusEnum definitionStatus;
    /** 激活状态。 */
    private ActivationStatusEnum activationStatus;
    /** 灰度发布状态。 */
    private GrayStatusEnum grayStatus;
    /** 灰度规则配置。 */
    private Map<String, Object> grayRuleConfig;
    /** 备注。 */
    private String remark;
    /** 创建人。 */
    private String createdBy;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新人。 */
    private String updatedBy;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 归档人。 */
    private String archivedBy;
    /** 归档时间。 */
    private LocalDateTime archivedAt;

    public ProcessDefinitionDTO() {
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(String definitionId) {
        this.definitionId = definitionId;
    }

    public String getProcessCode() {
        return processCode;
    }

    public void setProcessCode(String processCode) {
        this.processCode = processCode;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public String getSystemCode() {
        return systemCode;
    }

    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public DefinitionStatusEnum getDefinitionStatus() {
        return definitionStatus;
    }

    public void setDefinitionStatus(DefinitionStatusEnum definitionStatus) {
        this.definitionStatus = definitionStatus;
    }

    public ActivationStatusEnum getActivationStatus() {
        return activationStatus;
    }

    public void setActivationStatus(ActivationStatusEnum activationStatus) {
        this.activationStatus = activationStatus;
    }

    public GrayStatusEnum getGrayStatus() {
        return grayStatus;
    }

    public void setGrayStatus(GrayStatusEnum grayStatus) {
        this.grayStatus = grayStatus;
    }

    public Map<String, Object> getGrayRuleConfig() {
        return grayRuleConfig;
    }

    public void setGrayRuleConfig(Map<String, Object> grayRuleConfig) {
        this.grayRuleConfig = grayRuleConfig;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getArchivedBy() {
        return archivedBy;
    }

    public void setArchivedBy(String archivedBy) {
        this.archivedBy = archivedBy;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(LocalDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }
}

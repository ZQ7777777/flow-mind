package com.flowmind.platform.api.dto;

import com.flowmind.platform.api.enums.ActivationStatusEnum;
import com.flowmind.platform.api.enums.DefinitionStatusEnum;
import com.flowmind.platform.api.enums.GrayStatusEnum;

/**
 * 流程定义分页查询条件。
 */
public class ProcessDefinitionQuery extends PageQuery {

    /** 流程编码。 */
    private String processCode;
    /** 流程名称关键字。 */
    private String processName;
    /** 所属系统编码。 */
    private String systemCode;
    /** 流程定义状态。 */
    private DefinitionStatusEnum definitionStatus;
    /** 激活状态。 */
    private ActivationStatusEnum activationStatus;
    /** 灰度发布状态。 */
    private GrayStatusEnum grayStatus;
    public ProcessDefinitionQuery() {
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

}

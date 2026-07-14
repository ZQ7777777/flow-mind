package com.flowmind.platform.api.request;

import com.flowmind.platform.api.enums.ActivationStatus;
import com.flowmind.platform.api.enums.DefinitionStatus;
import com.flowmind.platform.api.enums.GrayStatus;

/**
 * 流程定义分页查询条件。
 */
public class ProcessDefinitionQuery {

    /** 流程编码。 */
    private String processCode;
    /** 流程名称关键字。 */
    private String processName;
    /** 所属系统编码。 */
    private String systemCode;
    /** 流程定义状态。 */
    private DefinitionStatus definitionStatus;
    /** 激活状态。 */
    private ActivationStatus activationStatus;
    /** 灰度发布状态。 */
    private GrayStatus grayStatus;
    /** 当前页码，从 1 开始。 */
    private Integer pageNo;
    /** 每页条数。 */
    private Integer pageSize;

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

    public DefinitionStatus getDefinitionStatus() {
        return definitionStatus;
    }

    public void setDefinitionStatus(DefinitionStatus definitionStatus) {
        this.definitionStatus = definitionStatus;
    }

    public ActivationStatus getActivationStatus() {
        return activationStatus;
    }

    public void setActivationStatus(ActivationStatus activationStatus) {
        this.activationStatus = activationStatus;
    }

    public GrayStatus getGrayStatus() {
        return grayStatus;
    }

    public void setGrayStatus(GrayStatus grayStatus) {
        this.grayStatus = grayStatus;
    }

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}

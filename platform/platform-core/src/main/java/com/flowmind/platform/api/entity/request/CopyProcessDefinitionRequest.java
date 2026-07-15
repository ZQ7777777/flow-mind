package com.flowmind.platform.api.entity.request;

/**
 * 复制流程定义请求。
 */
public class CopyProcessDefinitionRequest extends OperationRequest {

    /** 新流程编码；为空时沿用原流程编码。 */
    private String processCode;
    /** 新流程名称；为空时沿用原流程名称。 */
    private String processName;
    /** 所属系统编码。 */
    private String systemCode;
    /** 备注。 */
    private String remark;
    /** 操作人用户 ID。 */
    private String operatorUserId;

    public CopyProcessDefinitionRequest() {
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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(String operatorUserId) {
        this.operatorUserId = operatorUserId;
    }
}

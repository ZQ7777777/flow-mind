package com.flowmind.platform.api.request;

/**
 * 创建流程定义请求。
 */
public class CreateProcessDefinitionRequest extends OperationRequest {

    /** 流程编码。 */
    private String processCode;
    /** 流程名称。 */
    private String processName;
    /** 所属系统编码。 */
    private String systemCode;
    /** 备注。 */
    private String remark;
    /** 操作人用户 ID。 */
    private String operatorUserId;

    public CreateProcessDefinitionRequest() {
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

package com.flowmind.platform.api.request;

import java.util.List;
import java.util.Map;

public class StartProcessRequest extends OperationRequest {

    /** 要启动的流程定义业务编码。 */
    private String processCode;
    /** 外部系统用于关联该流程实例的业务键。 */
    private String businessKey;
    /** 展示给流程参与者的实例标题。 */
    private String instanceTitle;
    /** 发起流程的用户 ID。 */
    private String starterUserId;
    /** 发起流程时所属部门的 ID。 */
    private String starterDeptId;
    /** 启动时写入的流程变量，通常承载表单字段值。 */
    private Map<String, Object> variables;
    private List<AttachmentUploadItem> attachments;

    public StartProcessRequest() {
    }

    public String getProcessCode() {
        return processCode;
    }

    public void setProcessCode(String processCode) {
        this.processCode = processCode;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public String getInstanceTitle() {
        return instanceTitle;
    }

    public void setInstanceTitle(String instanceTitle) {
        this.instanceTitle = instanceTitle;
    }

    public String getStarterUserId() {
        return starterUserId;
    }

    public void setStarterUserId(String starterUserId) {
        this.starterUserId = starterUserId;
    }

    public String getStarterDeptId() {
        return starterDeptId;
    }

    public void setStarterDeptId(String starterDeptId) {
        this.starterDeptId = starterDeptId;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    public List<AttachmentUploadItem> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<AttachmentUploadItem> attachments) {
        this.attachments = attachments;
    }
}

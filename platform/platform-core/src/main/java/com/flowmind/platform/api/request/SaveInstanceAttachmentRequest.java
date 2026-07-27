package com.flowmind.platform.api.request;

/**
 * 保存实例级附件请求。
 */
public class SaveInstanceAttachmentRequest extends OperationRequest {

    /** 流程实例 ID。 */
    private String instanceId;
    /** 上传人用户 ID。 */
    private String operatorUserId;
    /** 实例附件的上传来源任务 ID。 */
    private String sourceTaskId;
    /** 来源任务读取时的乐观锁版本。 */
    private Long expectedTaskVersion;
    /** 附件上传项。 */
    private AttachmentUploadItem attachment;

    public SaveInstanceAttachmentRequest() {
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(String operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public String getSourceTaskId() { return sourceTaskId; }
    public void setSourceTaskId(String sourceTaskId) { this.sourceTaskId = sourceTaskId; }
    public Long getExpectedTaskVersion() { return expectedTaskVersion; }
    public void setExpectedTaskVersion(Long expectedTaskVersion) { this.expectedTaskVersion = expectedTaskVersion; }

    public AttachmentUploadItem getAttachment() {
        return attachment;
    }

    public void setAttachment(AttachmentUploadItem attachment) {
        this.attachment = attachment;
    }
}

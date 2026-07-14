package com.flowmind.platform.api.request;

/**
 * 保存任务级附件请求。
 */
public class SaveTaskAttachmentRequest extends OperationRequest {

    /** 流程实例 ID。 */
    private String instanceId;
    /** 任务 ID。 */
    private String taskId;
    /** 上传人用户 ID。 */
    private String operatorUserId;
    /** 附件上传项。 */
    private AttachmentUploadItem attachment;

    public SaveTaskAttachmentRequest() {
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(String operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public AttachmentUploadItem getAttachment() {
        return attachment;
    }

    public void setAttachment(AttachmentUploadItem attachment) {
        this.attachment = attachment;
    }
}

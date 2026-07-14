package com.flowmind.platform.api.request;

import com.flowmind.platform.api.enums.AttachmentOwnerType;

public class AttachmentAccessRequest {

    /** 发起附件访问请求的用户 ID。 */
    private String userId;
    /** 发起附件访问请求的用户名称。 */
    private String userName;
    /** 附件访问动作，例如上传、查看、下载或删除。 */
    private String accessAction;
    /** 附件所属流程实例 ID。 */
    private String instanceId;
    /** 附件关联任务 ID；实例级附件或未关联任务时可为空。 */
    private String taskId;
    /** 附件 ID；附件未保存前可为空。 */
    private String attachmentId;
    /** 附件归属类型。 */
    private AttachmentOwnerType ownerType;

    public AttachmentAccessRequest() {
    }

    public AttachmentAccessRequest(String userId, String userName, String accessAction, String instanceId,
            String taskId, String attachmentId, AttachmentOwnerType ownerType) {
        this.userId = userId;
        this.userName = userName;
        this.accessAction = accessAction;
        this.instanceId = instanceId;
        this.taskId = taskId;
        this.attachmentId = attachmentId;
        this.ownerType = ownerType;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getAccessAction() {
        return accessAction;
    }

    public void setAccessAction(String accessAction) {
        this.accessAction = accessAction;
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

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public AttachmentOwnerType getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(AttachmentOwnerType ownerType) {
        this.ownerType = ownerType;
    }
}

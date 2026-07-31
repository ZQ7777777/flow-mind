package com.flowmind.platform.api.request;

import lombok.Data;

/**
 * 使用当前申请返工任务原子替换实例级附件。
 *
 * @author FlowMind
 * @since 2026-07-30
 */
@Data
public class ReplaceInstanceAttachmentRequest extends OperationRequest {

    /** 流程实例 ID。 */
    private String instanceId;
    /** 要被替换的实例级附件 ID。 */
    private String attachmentId;
    /** 当前申请返工任务 ID。 */
    private String sourceTaskId;
    /** 当前申请返工任务读取时的乐观锁版本。 */
    private Long expectedTaskVersion;
    /** 实际执行替换的用户 ID。 */
    private String operatorUserId;
    /** 新附件内容；附件编码必须与旧附件一致。 */
    private AttachmentUploadItem attachment;
}

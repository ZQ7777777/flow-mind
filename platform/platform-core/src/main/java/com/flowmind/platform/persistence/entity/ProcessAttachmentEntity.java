package com.flowmind.platform.persistence.entity;
import lombok.Data;
import java.time.LocalDateTime;
/** 附件记录表 process_attachment 的持久化实体，仅保存文件元数据。
 *
 * @author Yuxin Xu
 * @since 2026-07-15 */
@Data
public class ProcessAttachmentEntity {
    /**
     * 附件记录主键。
     */
    private String id;
    /**
     * 所属流程实例 ID。
     */
    private String instanceId;
    /**
     * 所属任务 ID，实例级附件可为空。
     */
    private String taskId;
    /**
     * 附件归属类型，典型值：INSTANCE、TASK。
     */
    private String ownerType;
    /**
     * 附件业务编码。
     */
    private String attachmentCode;
    /**
     * 关联表单字段编码。
     */
    private String fieldCode;
    /**
     * 原始文件名。
     */
    private String fileName;
    /**
     * MIME 内容类型。
     */
    private String contentType;
    /**
     * 文件大小，单位为字节。
     */
    private Long sizeBytes;
    /**
     * 文件存储 SPI 返回的存储键。
     */
    private String storageKey;
    /**
     * 上传人 ID。
     */
    private String uploadedBy;
    /**
     * 上传时间。
     */
    private LocalDateTime uploadedAt;
    /**
     * 是否已逻辑删除。
     */
    private Boolean deleted;
    /**
     * 删除操作人 ID。
     */
    private String deletedBy;
    /**
     * 删除时间。
     */
    private LocalDateTime deletedAt;
}

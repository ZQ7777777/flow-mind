package com.flowmind.platform.persistence.entity;

import lombok.Data;
import java.time.LocalDateTime;

/** 附件模板表 process_attachment_template 的持久化实体。
 *
 * @author Yuxin Xu
 * @since 2026-07-15 */
@Data
public class ProcessAttachmentTemplateEntity {
    /**
     * 附件模板版本主键。
     */
    private String id;
    /**
     * 附件业务编码。
     */
    private String attachmentCode;
    /**
     * 同一附件编码下的模板版本号。
     */
    private Integer templateVersion;
    /**
     * 附件名称。
     */
    private String attachmentName;
    /**
     * 附件说明。
     */
    private String description;
    /**
     * 允许扩展名列表文本，如 pdf,jpg,png。
     */
    private String allowedExtensions;
    /**
     * 单个文件最大字节数。
     */
    private Long maxSizeBytes;
    /**
     * 模板状态，典型值：ENABLED、DISABLED。
     */
    private String templateStatus;
    /**
     * 创建人 ID。
     */
    private String createdBy;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新人 ID。
     */
    private String updatedBy;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;
}

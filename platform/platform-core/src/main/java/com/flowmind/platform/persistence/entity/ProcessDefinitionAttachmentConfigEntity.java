package com.flowmind.platform.persistence.entity;

import lombok.Data;
import java.time.LocalDateTime;

/** 流程附件配置表 process_definition_attachment_config 的持久化实体。 @author Yuxin Xu @since 2026-07-15 */
@Data
public class ProcessDefinitionAttachmentConfigEntity {
    /**
     * 附件配置记录主键。
     */
    private String id;
    /**
     * 同批配置记录共享的配置组 ID。
     */
    private String attachmentConfigId;
    /**
     * 所属流程定义 ID。
     */
    private String definitionId;
    /**
     * 配置状态，典型值：DRAFT、ACTIVE、INACTIVE。
     */
    private String configStatus;
    /**
     * 配置组激活时间。
     */
    private LocalDateTime activatedAt;
    /**
     * 引用的附件模板版本 ID。
     */
    private String attachmentTemplateId;
    /**
     * 附件业务编码。
     */
    private String attachmentCode;
    /**
     * 当前配置是否必填。
     */
    private Boolean required;
    /**
     * 最少附件数量。
     */
    private Integer minCount;
    /**
     * 最多附件数量，为空表示不限制。
     */
    private Integer maxCount;
    /**
     * 适用节点编码 JSON 数组，如 ["apply"]。
     */
    private String applicableNodeCodes;
    /**
     * 配置展示顺序。
     */
    private Integer sortOrder;
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

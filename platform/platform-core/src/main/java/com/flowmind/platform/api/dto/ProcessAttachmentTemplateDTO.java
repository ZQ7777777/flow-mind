package com.flowmind.platform.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 流程附件模板配置返回对象，合并展示全局附件模板版本和流程定义附件配置。
 *
 * @author Yuxin Xu
 * @created 2026-07-14
 */
@Data
public class ProcessAttachmentTemplateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 附件配置记录主键。
     */
    private String id;
    /**
     * 附件配置组 ID，同一组内可包含多个附件配置项。
     */
    private String attachmentConfigId;
    /**
     * 所属流程定义 ID。
     */
    private String definitionId;
    /**
     * 附件配置状态，如 DRAFT、ACTIVE、INACTIVE。
     */
    private String configStatus;
    /**
     * 附件配置组生效时间。
     */
    private LocalDateTime activatedAt;
    /**
     * 全局附件模板版本 ID。
     */
    private String attachmentTemplateId;
    /**
     * 附件编码。
     */
    private String attachmentCode;
    /**
     * 附件模板版本号。
     */
    private Integer templateVersion;
    /**
     * 附件名称。
     */
    private String attachmentName;
    /**
     * 附件模板说明。
     */
    private String description;
    /**
     * 允许上传的文件扩展名列表。
     */
    private List<String> allowedExtensions = new ArrayList<String>();
    /**
     * 单文件大小限制，单位字节。
     */
    private Long maxSizeBytes;
    /**
     * 附件模板状态，如 ENABLED、DISABLED。
     */
    private String templateStatus;
    /**
     * 当前流程附件配置下是否必填。
     */
    private Boolean required;
    /**
     * 当前流程附件配置下最小上传数量。
     */
    private Integer minCount;
    /**
     * 当前流程附件配置下最大上传数量。
     */
    private Integer maxCount;
    /**
     * 当前流程附件配置适用的节点编码列表。
     */
    private List<String> applicableNodeCodes = new ArrayList<String>();
    /**
     * 附件配置展示顺序。
     */
    private Integer sortOrder;
    /**
     * 创建人。
     */
    private String createdBy;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新人。
     */
    private String updatedBy;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;
}

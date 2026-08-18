package com.flowmind.business.entry;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Business Base 维护的流程定义入口配置记录。
 *
 * @author FlowMind
 * @since 2026-08-18
 */
@Data
public class BusinessEntryConfigEntity {
    /** 配置主键。 */
    private String id;
    /** 绑定的流程定义 ID，同一定义最多一条入口配置。 */
    private String definitionId;
    /** 业务大厅展示名称；为空时使用流程名称。 */
    private String entryDisplayName;
    /** 同源前端录入页路由。 */
    private String entryPageUrl;
    /** 配置来源。 */
    private BusinessEntrySource entrySource;
    /** 是否在业务大厅启用。 */
    private Boolean enabled;
    /** 配置备注。 */
    private String remark;
    /** Agent 生成批次 ID。 */
    private String generationId;
    /** Agent 产物修订号。 */
    private String artifactRevision;
    /** 创建人。 */
    private String createdBy;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最近更新人。 */
    private String updatedBy;
    /** 最近更新时间。 */
    private LocalDateTime updatedAt;
}

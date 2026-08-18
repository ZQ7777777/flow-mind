package com.flowmind.business.entry.dto;

import com.flowmind.business.entry.BusinessEntrySource;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端业务入口配置响应。
 *
 * @author FlowMind
 * @since 2026-08-18
 */
@Data
public class BusinessEntryConfigResponse {
    /** 配置主键。 */
    private String id;
    /** 流程定义 ID。 */
    private String definitionId;
    /** 流程编码。 */
    private String processCode;
    /** 流程名称。 */
    private String processName;
    /** 流程定义版本。 */
    private Integer definitionVersion;
    /** 最终展示名称。 */
    private String entryDisplayName;
    /** 同源前端录入页路由。 */
    private String entryPageUrl;
    /** 配置来源。 */
    private BusinessEntrySource entrySource;
    /** 是否启用。 */
    private Boolean enabled;
    /** 备注。 */
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

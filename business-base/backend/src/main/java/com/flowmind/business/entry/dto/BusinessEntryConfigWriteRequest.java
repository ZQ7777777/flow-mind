package com.flowmind.business.entry.dto;

import com.flowmind.business.entry.BusinessEntrySource;
import lombok.Data;

/**
 * 新建或更新业务入口配置的请求。
 *
 * @author FlowMind
 * @since 2026-08-18
 */
@Data
public class BusinessEntryConfigWriteRequest {
    /** 创建时绑定的流程定义 ID；按定义 upsert 时由路径提供。 */
    private String definitionId;
    /** 业务大厅展示名称；空白时回退为流程名称。 */
    private String entryDisplayName;
    /** 同源前端录入页路由。 */
    private String entryPageUrl;
    /** 配置来源；创建时可按调用入口使用默认值。 */
    private BusinessEntrySource entrySource;
    /** 是否在业务大厅启用；新配置默认关闭。 */
    private Boolean enabled;
    /** 配置备注。 */
    private String remark;
    /** Agent 生成批次 ID。 */
    private String generationId;
    /** Agent 产物修订号。 */
    private String artifactRevision;
}

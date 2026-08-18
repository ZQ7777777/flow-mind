package com.flowmind.business.entry.dto;

import com.flowmind.business.entry.BusinessEntrySource;
import lombok.Data;

/**
 * 业务大厅使用的流程录入页入口。
 *
 * @author FlowMind
 * @since 2026-08-18
 */
@Data
public class ProcessEntryLinkResponse {
    /** 流程定义 ID。 */
    private String definitionId;
    /** 流程编码。 */
    private String processCode;
    /** 流程名称。 */
    private String processName;
    /** 流程定义版本。 */
    private Integer definitionVersion;
    /** 业务大厅展示名称。 */
    private String entryDisplayName;
    /** 同源前端录入页路由。 */
    private String entryPageUrl;
    /** 配置来源。 */
    private BusinessEntrySource entrySource;
    /** 是否启用；公共接口返回值恒为 true。 */
    private Boolean enabled;
}

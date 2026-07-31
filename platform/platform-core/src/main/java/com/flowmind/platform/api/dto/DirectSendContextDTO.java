package com.flowmind.platform.api.dto;

import lombok.Data;

/**
 * 活动任务的可信直送上下文。
 *
 * <p>目标节点由平台根据创建当前返工任务的驳回历史解析，调用方不得自行推断。</p>
 *
 * @author FlowMind
 * @since 2026-07-30
 */
@Data
public class DirectSendContextDTO {

    /** 当前活动任务 ID。 */
    private String taskId;
    /** 当前任务是否满足直送条件。 */
    private boolean allowed;
    /** 本次驳回来源节点编码；不可直送时为空。 */
    private String targetNodeCode;
    /** 本次驳回来源节点名称；不可直送时为空。 */
    private String targetNodeName;
}

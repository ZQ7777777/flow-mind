package com.flowmind.platform.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 自动知会节点产生的消息快照。
 *
 * @author FlowMind
 * @since 1.0.0
 */
@Data
public class ProcessNoticeDTO {

    /** 知会节点编码。 */
    private String nodeCode;
    /** 知会节点名称。 */
    private String nodeName;
    /** 流程实例标题。 */
    private String instanceTitle;
    /** 消息标题。 */
    private String title;
    /** 消息正文。 */
    private String content;
    /** 接收用户 ID 列表。 */
    private List<String> targetUserIds;
}

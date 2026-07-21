package com.flowmind.platform.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 消息发布 SPI 使用的公共消息载荷。
 *
 * @author FlowMind
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessMessage {

    /** 消息 ID。 */
    private String messageId;
    /** 消息类型。 */
    private String messageType;
    /** 消息标题。 */
    private String title;
    /** 消息正文。 */
    private String content;
    /** 消息接收用户 ID 列表。 */
    private List<String> targetUserIds;
    /** 消息扩展载荷。 */
    private Map<String, Object> payload;
    /** 消息创建时间。 */
    private LocalDateTime createdAt;

}

package com.flowmind.platform.api.spi;

import com.flowmind.platform.persistence.entity.ProcessMessage;

/**
 * 消息推送 SPI。
 */
public interface MessagePublisher {
    /**
     * 发送消息。
     *
     * @param message 消息内容
     */
    void publish(ProcessMessage message);
}

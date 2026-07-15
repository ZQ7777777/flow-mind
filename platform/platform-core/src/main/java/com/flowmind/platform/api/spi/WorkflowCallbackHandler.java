package com.flowmind.platform.api.spi;

import com.flowmind.platform.persistence.entity.WorkflowEvent;

/**
 * 工作流回调处理 SPI。
 */
public interface WorkflowCallbackHandler {
    /**
     * 处理回调事件。
     *
     * @param event 回调事件
     */
    void handle(WorkflowEvent event);
}

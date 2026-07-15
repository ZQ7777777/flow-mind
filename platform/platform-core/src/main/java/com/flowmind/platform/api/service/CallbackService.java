package com.flowmind.platform.api.service;

import com.flowmind.platform.api.entity.dto.CallbackLogDTO;
import com.flowmind.platform.api.entity.result.PageResult;
import com.flowmind.platform.api.entity.po.WorkflowEvent;
import com.flowmind.platform.api.entity.query.CallbackLogQuery;

/**
 * 回调服务。
 */
public interface CallbackService {
    /**
     * 发布回调事件。
     *
     * @param event 工作流事件
     */
    void publishCallback(WorkflowEvent event);

    /**
     * 分页查询回调日志。
     *
     * @param query 查询条件
     * @return 回调日志分页结果
     */
    PageResult<CallbackLogDTO> queryCallbackLogs(CallbackLogQuery query);
}

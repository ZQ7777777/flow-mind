package com.flowmind.platform.api.service;

import com.flowmind.platform.api.dto.ReadRecordDTO;

/**
 * 流程已阅记录写入服务。
 *
 * @author FlowMind
 * @since 2026-08-10
 */
public interface ReadRecordService {

    /**
     * 使用可信当前用户幂等标记流程实例为已阅。
     *
     * @param instanceId 流程实例 ID
     * @return 已阅记录
     */
    ReadRecordDTO markRead(String instanceId);
}

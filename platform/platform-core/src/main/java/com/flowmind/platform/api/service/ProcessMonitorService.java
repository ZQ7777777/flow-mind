package com.flowmind.platform.api.service;

import com.flowmind.platform.api.entity.dto.AlertDTO;
import com.flowmind.platform.api.entity.result.PageResult;
import com.flowmind.platform.api.entity.dto.ReminderDTO;
import com.flowmind.platform.api.entity.dto.TaskDTO;
import com.flowmind.platform.api.entity.query.AlertQuery;
import com.flowmind.platform.api.entity.request.HandleAlertRequest;
import com.flowmind.platform.api.entity.request.RemindTaskRequest;
import com.flowmind.platform.api.entity.query.ReminderQuery;
import com.flowmind.platform.api.entity.request.TimeoutScanRequest;

import java.util.List;

/**
 * 流程监控、提醒和告警服务。
 */
public interface ProcessMonitorService {
    /**
     * 手动催办任务。
     *
     * @param request 催办请求
     * @return 提醒记录
     */
    ReminderDTO remindTask(RemindTaskRequest request);

    /**
     * 分页查询提醒记录。
     *
     * @param query 查询条件
     * @return 提醒记录分页结果
     */
    PageResult<ReminderDTO> queryReminders(ReminderQuery query);

    /**
     * 扫描超时任务。
     *
     * @param request 扫描请求
     * @return 超时任务列表
     */
    List<TaskDTO> scanTimeoutTasks(TimeoutScanRequest request);

    /**
     * 分页查询告警记录。
     *
     * @param query 查询条件
     * @return 告警分页结果
     */
    PageResult<AlertDTO> queryAlerts(AlertQuery query);

    /**
     * 处理告警。
     *
     * @param request 处理请求
     * @return 告警记录
     */
    AlertDTO handleAlert(HandleAlertRequest request);
}

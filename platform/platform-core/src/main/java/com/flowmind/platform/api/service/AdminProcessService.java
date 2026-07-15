package com.flowmind.platform.api.service;

import com.flowmind.platform.api.dto.AuditLogDTO;
import com.flowmind.platform.api.dto.CallbackLogDTO;
import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.TaskActionResult;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.AdminHistoryTaskQuery;
import com.flowmind.platform.api.dto.AdminInstanceQuery;
import com.flowmind.platform.api.dto.AdminTaskQuery;
import com.flowmind.platform.api.dto.AuditLogQuery;
import com.flowmind.platform.api.dto.CallbackLogQuery;
import com.flowmind.platform.api.request.ForceCompleteRequest;
import com.flowmind.platform.api.request.JumpNodeRequest;

/**
 * 管理端流程操作与查询服务。
 */
public interface AdminProcessService {
    /**
     * 将流程跳转到指定节点。
     *
     * @param request 跳转请求
     * @return 任务动作结果
     */
    TaskActionResult jumpToNode(JumpNodeRequest request);

    /**
     * 强制办结流程实例。
     *
     * @param request 强制办结请求
     * @return 流程实例概要
     */
    ProcessInstanceDTO forceComplete(ForceCompleteRequest request);

    /**
     * 分页查询流程实例。
     *
     * @param query 查询条件
     * @return 流程实例分页结果
     */
    PageResult<ProcessInstanceDTO> queryInstances(AdminInstanceQuery query);

    /**
     * 分页查询活动任务。
     *
     * @param query 查询条件
     * @return 活动任务分页结果
     */
    PageResult<TaskDTO> queryActiveTasks(AdminTaskQuery query);

    /**
     * 分页查询历史任务。
     *
     * @param query 查询条件
     * @return 历史任务分页结果
     */
    PageResult<HistoryTaskDTO> queryHistoryTasks(AdminHistoryTaskQuery query);

    /**
     * 分页查询审计日志。
     *
     * @param query 查询条件
     * @return 审计日志分页结果
     */
    PageResult<AuditLogDTO> queryAuditLogs(AuditLogQuery query);

    /**
     * 分页查询回调日志。
     *
     * @param query 查询条件
     * @return 回调日志分页结果
     */
    PageResult<CallbackLogDTO> queryCallbackLogs(CallbackLogQuery query);
}

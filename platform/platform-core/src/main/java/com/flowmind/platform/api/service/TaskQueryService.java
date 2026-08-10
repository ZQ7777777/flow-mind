package com.flowmind.platform.api.service;

import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessCommentDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.ReadRecordDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.CompletedTaskQuery;
import com.flowmind.platform.api.dto.ReadRecordQuery;
import com.flowmind.platform.api.dto.StartedInstanceQuery;
import com.flowmind.platform.api.dto.TodoTaskQuery;

import java.util.List;

/**
 * 任务查询服务。
 */
public interface TaskQueryService {
    /**
     * 按任务 ID 查询活动任务。
     *
     * @param taskId 活动任务 ID
     * @return 活动任务
     */
    TaskDTO getTask(String taskId);

    /**
     * 分页查询待办任务。
     *
     * @param query 查询条件
     * @return 待办任务分页结果
     */
    PageResult<TaskDTO> queryTodoTasks(TodoTaskQuery query);

    /**
     * 分页查询已办任务。
     *
     * @param query 查询条件
     * @return 已办任务分页结果
     */
    PageResult<HistoryTaskDTO> queryCompletedTasks(CompletedTaskQuery query);

    /**
     * 分页查询我发起的流程实例。
     *
     * @param query 查询条件
     * @return 流程实例分页结果
     */
    PageResult<ProcessInstanceDTO> queryStartedInstances(StartedInstanceQuery query);

    /**
     * 查询流程实例的活动任务。
     *
     * @param instanceId 流程实例 ID
     * @return 活动任务列表
     */
    List<TaskDTO> queryActiveTasks(String instanceId);

    /**
     * 查询流程实例的历史任务。
     *
     * @param instanceId 流程实例 ID
     * @return 历史任务列表
     */
    List<HistoryTaskDTO> queryHistoryTasks(String instanceId);

    /**
     * 查询流程实例的审批意见。
     *
     * @param instanceId 流程实例 ID
     * @return 审批意见列表
     */
    List<ProcessCommentDTO> queryComments(String instanceId);

    /**
     * 分页查询已阅记录。
     *
     * @param query 查询条件
     * @return 已阅记录分页结果
     */
    PageResult<ReadRecordDTO> queryReadRecords(ReadRecordQuery query);
}

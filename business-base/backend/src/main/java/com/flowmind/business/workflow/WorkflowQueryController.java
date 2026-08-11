package com.flowmind.business.workflow;

import com.flowmind.business.workflow.dto.WorkflowDetailResponse;
import com.flowmind.business.workflow.dto.WorkflowHistoryTaskResponse;
import com.flowmind.business.workflow.dto.WorkflowInstanceResponse;
import com.flowmind.business.workflow.dto.WorkflowListQuery;
import com.flowmind.business.workflow.dto.WorkflowPageResponse;
import com.flowmind.business.workflow.dto.WorkflowReadRecordResponse;
import com.flowmind.business.workflow.dto.WorkflowTaskResponse;
import com.flowmind.business.workflow.dto.WorkflowUserCandidateResponse;
import com.flowmind.business.workflow.dto.WorkflowUserResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 通用流程查询接口，为业务前端提供用户列表、流程详情和已阅能力。
 */
@RestController
@RequestMapping("/api/workflow")
public class WorkflowQueryController {
    private final WorkflowQueryService queryService;
    private final WorkflowUserSearchService userSearchService;

    public WorkflowQueryController(WorkflowQueryService queryService, WorkflowUserSearchService userSearchService) {
        this.queryService = queryService;
        this.userSearchService = userSearchService;
    }

    /**
     * 查询服务端可信上下文中的当前用户。
     *
     * @return 当前用户及所属部门信息
     */
    @GetMapping("/me")
    public WorkflowUserResponse currentUser() { return queryService.currentUser(); }

    /**
     * 分页查询当前用户的待办任务。
     *
     * @param query 分页和业务筛选条件，不包含可生效的用户身份
     * @return 当前用户待办任务分页结果
     */
    /**
     * 查询可作为转办、委托或加签目标的用户候选。
     *
     * @param keyword 用户 ID、姓名或部门关键字
     * @param limit 最大返回数量
     * @return 用户候选列表
     */
    @GetMapping("/users")
    public List<WorkflowUserCandidateResponse> users(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) Integer limit) {
        return userSearchService.search(keyword, limit);
    }
    @GetMapping("/tasks/todo")
    public WorkflowPageResponse<WorkflowTaskResponse> todo(@ModelAttribute WorkflowListQuery query) { return queryService.todo(query); }

    /**
     * 分页查询当前用户已处理的历史任务。
     *
     * @param query 分页和业务筛选条件
     * @return 当前用户已办任务分页结果
     */
    @GetMapping("/tasks/completed")
    public WorkflowPageResponse<WorkflowHistoryTaskResponse> completed(@ModelAttribute WorkflowListQuery query) { return queryService.completed(query); }

    /**
     * 分页查询当前用户发起的流程实例。
     *
     * @param query 分页和业务筛选条件
     * @return 当前用户发起的流程实例分页结果
     */
    @GetMapping("/instances/started")
    public WorkflowPageResponse<WorkflowInstanceResponse> started(@ModelAttribute WorkflowListQuery query) { return queryService.started(query); }

    /**
     * 分页查询当前用户的已阅记录，并补充实例展示信息。
     *
     * @param query 分页和实例筛选条件
     * @return 当前用户已阅记录分页结果
     */
    @GetMapping("/read-records")
    public WorkflowPageResponse<WorkflowReadRecordResponse> readRecords(@ModelAttribute WorkflowListQuery query) { return queryService.readRecords(query); }

    /**
     * 按活动任务查询可审批的流程详情，保留当前任务版本和允许动作。
     *
     * @param taskId 活动任务 ID
     * @return 聚合后的通用流程详情
     */
    @GetMapping("/tasks/{taskId}")
    public WorkflowDetailResponse taskDetail(@PathVariable String taskId) { return queryService.taskDetail(taskId); }

    /**
     * 按流程实例查询只读详情。
     *
     * @param instanceId 流程实例 ID
     * @return 聚合后的通用流程详情
     */
    @GetMapping("/instances/{instanceId}")
    public WorkflowDetailResponse instanceDetail(@PathVariable String instanceId) { return queryService.instanceDetail(instanceId); }

    /**
     * 将当前用户对指定流程实例的访问标记为已阅。
     *
     * @param instanceId 流程实例 ID
     * @return 幂等写入后的已阅记录
     */
    @PostMapping("/instances/{instanceId}/read")
    public WorkflowReadRecordResponse markRead(@PathVariable String instanceId) { return queryService.markRead(instanceId); }
}

package com.flowmind.business.workflow;

import com.flowmind.business.platform.PlatformDtoMapper;
import com.flowmind.business.platform.PlatformFacade;
import com.flowmind.business.security.WorkflowAccessGuard;
import com.flowmind.business.workflow.dto.WorkflowDetailResponse;
import com.flowmind.business.workflow.dto.WorkflowHistoryTaskResponse;
import com.flowmind.business.workflow.dto.WorkflowInstanceResponse;
import com.flowmind.business.workflow.dto.WorkflowListQuery;
import com.flowmind.business.workflow.dto.WorkflowPageResponse;
import com.flowmind.business.workflow.dto.WorkflowReadRecordResponse;
import com.flowmind.business.workflow.dto.WorkflowTaskResponse;
import com.flowmind.business.workflow.dto.WorkflowUserResponse;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDetailDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.ReadRecordDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 通用流程查询服务，负责可信身份校验、详情聚合、字段脱敏和已阅写入。
 */
@Service
public class WorkflowQueryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowQueryService.class);

    private final PlatformFacade platformFacade;
    private final PlatformDtoMapper mapper;
    private final WorkflowAccessGuard accessGuard;
    private final WorkflowAllowedActionResolver allowedActionResolver;
    private final WorkflowWithdrawContextResolver withdrawContextResolver = new WorkflowWithdrawContextResolver();

    public WorkflowQueryService(PlatformFacade platformFacade, PlatformDtoMapper mapper,
                                WorkflowAccessGuard accessGuard,
                                WorkflowAllowedActionResolver allowedActionResolver) {
        this.platformFacade = platformFacade;
        this.mapper = mapper;
        this.accessGuard = accessGuard;
        this.allowedActionResolver = allowedActionResolver;
    }

    /**
     * 获取服务端认证上下文中的当前业务用户。
     *
     * @return 当前用户稳定视图
     */
    public WorkflowUserResponse currentUser() { return mapper.user(platformFacade.currentUser()); }

    /**
     * 查询当前用户待办并映射为业务前端稳定分页结构。
     *
     * @param query 不含用户身份的查询条件
     * @return 待办分页结果
     */
    public WorkflowPageResponse<WorkflowTaskResponse> todo(WorkflowListQuery query) {
        PageResult<TaskDTO> page = platformFacade.todo(query);
        UserContext user = platformFacade.currentUser();
        return mapper.taskPage(filterExecutableTodos(page, user.getUserId()));
    }

    private PageResult<TaskDTO> filterExecutableTodos(PageResult<TaskDTO> source, String userId) {
        List<TaskDTO> sourceRecords = source.getRecords() == null
                ? Collections.<TaskDTO>emptyList() : source.getRecords();
        List<TaskDTO> visibleRecords = new ArrayList<TaskDTO>();
        for (TaskDTO task : sourceRecords) {
            if (!allowedActionResolver.resolve(task, null, sourceRecords,
                    Collections.<com.flowmind.platform.api.dto.HistoryTaskDTO>emptyList(), userId).isEmpty()) {
                visibleRecords.add(task);
            }
        }
        if (visibleRecords.size() == sourceRecords.size()) return source;
        PageResult<TaskDTO> filtered = new PageResult<TaskDTO>();
        filtered.setPageNo(source.getPageNo());
        filtered.setPageSize(source.getPageSize());
        filtered.setRecords(visibleRecords);
        long hiddenOnPage = sourceRecords.size() - visibleRecords.size();
        long total = Math.max(0L, (source.getTotal() == null ? sourceRecords.size() : source.getTotal()) - hiddenOnPage);
        filtered.setTotal(Long.valueOf(total));
        filtered.setTotalPages(totalPages(total, source.getPageSize()));
        return filtered;
    }

    private Integer totalPages(long total, Integer pageSize) {
        if (pageSize == null || pageSize.intValue() <= 0) return Integer.valueOf(0);
        return Integer.valueOf((int) ((total + pageSize.longValue() - 1L) / pageSize.longValue()));
    }
    /**
     * 查询当前用户已办并移除平台内部历史字段。
     *
     * @param query 不含用户身份的查询条件
     * @return 已办分页结果
     */
    public WorkflowPageResponse<WorkflowHistoryTaskResponse> completed(WorkflowListQuery query) {
        PageResult<HistoryTaskDTO> source = platformFacade.completed(query);
        WorkflowPageResponse<WorkflowHistoryTaskResponse> response = mapper.historyPage(source);
        enrichWithdrawContexts(response.getRecords(), platformFacade.currentUser().getUserId());
        return response;
    }

    /**
     * 每个实例仅加载一次运行详情，并且只给 Platform 认定的上一有效办理历史行附加撤回上下文。
     */
    private void enrichWithdrawContexts(List<WorkflowHistoryTaskResponse> records, String userId) {
        Map<String, WorkflowWithdrawContextResolver.Resolution> resolutions =
                new HashMap<String, WorkflowWithdrawContextResolver.Resolution>();
        for (WorkflowHistoryTaskResponse record : records == null
                ? Collections.<WorkflowHistoryTaskResponse>emptyList() : records) {
            String instanceId = record.getInstanceId();
            if (!resolutions.containsKey(instanceId)) {
                WorkflowWithdrawContextResolver.Resolution resolution = null;
                try {
                    resolution = withdrawContextResolver.resolve(platformFacade.getInstance(instanceId), userId);
                } catch (RuntimeException exception) {
                    LOGGER.warn("Could not resolve withdraw context for instanceId={}", instanceId);
                }
                resolutions.put(instanceId, resolution);
            }
            WorkflowWithdrawContextResolver.Resolution resolution = resolutions.get(instanceId);
            if (resolution == null || resolution.getSourceHistory() == null
                    || !record.getHistoryTaskId().equals(resolution.getSourceHistory().getHistoryTaskId())) continue;
            TaskDTO task = resolution.getActiveTask();
            WorkflowHistoryTaskResponse.WithdrawContext context =
                    new WorkflowHistoryTaskResponse.WithdrawContext();
            context.setTaskId(task.getTaskId());
            context.setExpectedTaskVersion(task.getTaskVersion());
            context.setTargetNodeCode(record.getNodeCode());
            context.setTargetNodeName(record.getNodeName());
            record.setWithdrawContext(context);
        }
    }

    /**
     * 查询当前用户发起的流程实例。
     *
     * @param query 不含用户身份的查询条件
     * @return 我发起的流程分页结果
     */
    public WorkflowPageResponse<WorkflowInstanceResponse> started(WorkflowListQuery query) { return mapper.instancePage(platformFacade.started(query)); }

    /**
     * 查询当前用户已阅记录，并按页补充流程名称、标题和状态。
     * 单条实例补充失败时保留原已阅记录，不影响整页返回。
     *
     * @param query 已阅分页和实例筛选条件
     * @return 已阅记录分页结果
     */
    public WorkflowPageResponse<WorkflowReadRecordResponse> readRecords(WorkflowListQuery query) {
        com.flowmind.platform.api.dto.PageResult<ReadRecordDTO> page = platformFacade.readRecords(query);
        Map<String, ProcessInstanceDTO> instances = new LinkedHashMap<String, ProcessInstanceDTO>();
        for (ReadRecordDTO record : page.getRecords()) {
            try {
                instances.put(record.getInstanceId(), platformFacade.getInstance(record.getInstanceId()));
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not enrich read record for instanceId={}", record.getInstanceId());
            }
        }
        return mapper.readPage(page, instances);
    }

    /**
     * 通过活动任务定位实例并聚合审批详情。
     *
     * @param taskId 活动任务 ID
     * @return 包含当前任务版本和允许动作的详情
     */
    public WorkflowDetailResponse taskDetail(String taskId) {
        TaskDTO task = platformFacade.getTask(taskId);
        return detail(task.getInstanceId(), task);
    }

    /**
     * 通过实例 ID 聚合只读流程详情。
     *
     * @param instanceId 流程实例 ID
     * @return 通用只读详情
     */
    public WorkflowDetailResponse instanceDetail(String instanceId) { return detail(instanceId, null); }

    /**
     * 在实例访问权限校验通过后显式标记当前用户已阅。
     *
     * @param instanceId 流程实例 ID
     * @return 标记后的已阅记录
     */
    public WorkflowReadRecordResponse markRead(String instanceId) {
        ProcessInstanceDetailDTO instance = platformFacade.getInstance(instanceId);
        UserContext user = platformFacade.currentUser();
        accessGuard.check(instance, instance.getActiveTasks(), instance.getHistoryTasks(), user.getUserId());
        ReadRecordDTO record = platformFacade.markRead(instanceId);
        com.flowmind.platform.api.dto.PageResult<ReadRecordDTO> page = new com.flowmind.platform.api.dto.PageResult<ReadRecordDTO>();
        page.setRecords(java.util.Collections.singletonList(record)); page.setPageNo(1); page.setPageSize(1);
        page.setTotal(1L); page.setTotalPages(1);
        Map<String, ProcessInstanceDTO> instances = java.util.Collections.<String, ProcessInstanceDTO>singletonMap(instanceId, instance);
        return mapper.readPage(page, instances).getRecords().get(0);
    }

    /**
     * 为任务动作加载实例并执行通用访问门禁，平台仍负责最终办理权限判断。
     *
     * @param taskId 活动任务 ID
     * @return 已通过实例访问校验的平台详情
     */
    public ProcessInstanceDetailDTO authorizedTaskInstance(String taskId) {
        TaskDTO task = platformFacade.getTask(taskId);
        ProcessInstanceDetailDTO instance = platformFacade.getInstance(task.getInstanceId());
        UserContext user = platformFacade.currentUser();
        accessGuard.check(instance, instance.getActiveTasks(), instance.getHistoryTasks(), user.getUserId());
        return instance;
    }

    /**
     * 聚合定义、字段、轨迹、意见、附件和允许动作，并在成功读取后尝试标记已阅。
     */
    private WorkflowDetailResponse detail(String instanceId, TaskDTO currentTask) {
        ProcessInstanceDetailDTO instance = platformFacade.getInstance(instanceId);
        UserContext user = platformFacade.currentUser();
        accessGuard.check(instance, instance.getActiveTasks(), instance.getHistoryTasks(), user.getUserId());
        ProcessDefinitionDetailDTO definition = platformFacade.getDefinition(instance.getDefinitionId());
        java.util.List<String> actions = allowedActionResolver.resolve(currentTask, definition, instance,
                instance.getActiveTasks(), instance.getHistoryTasks(), user.getUserId());
        List<ProcessNodeDTO> rejectTargetNodes = currentTask == null || !actions.contains("REJECT")
                ? Collections.<ProcessNodeDTO>emptyList()
                : platformFacade.rejectTargetNodes(currentTask.getTaskId());
        if (actions.contains("REJECT") && rejectTargetNodes.isEmpty()) {
            actions.remove("REJECT");
        }
        java.util.List<String> disabledActions = allowedActionResolver.resolveDisabled(currentTask, actions, user.getUserId());
        WorkflowDetailResponse response = mapper.detail(instance, definition, currentTask,
                instance.getActiveTasks(), instance.getHistoryTasks(), instance.getComments(),
                platformFacade.attachments(instanceId), rejectTargetNodes, actions, disabledActions);
        try {
            platformFacade.markRead(instanceId);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not mark workflow instance as read, instanceId={}", instanceId);
        }
        return response;
    }
}

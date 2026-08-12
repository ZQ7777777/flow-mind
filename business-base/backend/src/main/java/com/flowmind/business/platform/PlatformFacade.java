package com.flowmind.business.platform;

import com.flowmind.business.workflow.dto.WorkflowListQuery;
import com.flowmind.platform.api.dto.AttachmentDTO;
import com.flowmind.platform.api.dto.AttachmentQuery;
import com.flowmind.platform.api.dto.CompletedTaskQuery;
import com.flowmind.platform.api.dto.DirectSendContextDTO;
import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDetailDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.ReadRecordDTO;
import com.flowmind.platform.api.dto.ReadRecordQuery;
import com.flowmind.platform.api.dto.StartedInstanceQuery;
import com.flowmind.platform.api.dto.TaskActionResult;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.TodoTaskQuery;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.request.TaskOperationRequest;
import com.flowmind.platform.api.request.ReplaceInstanceAttachmentRequest;
import com.flowmind.platform.api.service.AttachmentService;
import com.flowmind.platform.api.service.ProcessDefinitionService;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import com.flowmind.platform.api.service.ReadRecordService;
import com.flowmind.platform.api.service.TaskQueryService;
import com.flowmind.platform.api.spi.CurrentUserProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PlatformFacade {
    private final TaskQueryService taskQueryService;
    private final ProcessRuntimeService runtimeService;
    private final ProcessDefinitionService definitionService;
    private final AttachmentService attachmentService;
    private final ReadRecordService readRecordService;
    private final CurrentUserProvider currentUserProvider;

    public PlatformFacade(TaskQueryService taskQueryService,
                          ProcessRuntimeService runtimeService,
                          ProcessDefinitionService definitionService,
                          AttachmentService attachmentService,
                          ReadRecordService readRecordService,
                          CurrentUserProvider currentUserProvider) {
        this.taskQueryService = taskQueryService;
        this.runtimeService = runtimeService;
        this.definitionService = definitionService;
        this.attachmentService = attachmentService;
        this.readRecordService = readRecordService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * 取得服务端可信身份，业务接口不得使用客户端传入的用户 ID 覆盖当前用户。
     */
    public UserContext currentUser() { return currentUserProvider.getCurrentUser(); }

    /**
     * 查询当前用户待办，并把业务侧稳定查询参数转换为平台待办查询契约。
     */
    public PageResult<TaskDTO> todo(WorkflowListQuery source) {
        TodoTaskQuery query = new TodoTaskQuery();
        page(source, query); query.setProcessCode(source.getProcessCode()); query.setProcessName(source.getProcessName());
        query.setInstanceTitle(source.getInstanceTitle()); query.setStarterUserId(source.getStarterUserId());
        query.setNodeCode(source.getNodeCode()); query.setTaskStatus(source.getStatus());
        query.setTodoSource(source.getSource()); query.setCreatedFrom(source.getFrom()); query.setCreatedTo(source.getTo());
        query.setSortBy(source.getSortBy()); query.setSortDirection(source.getSortDirection());
        return taskQueryService.queryTodoTasks(query);
    }

    /**
     * 查询当前用户已办任务，用户归属由平台侧可信身份再次校验。
     */
    public PageResult<HistoryTaskDTO> completed(WorkflowListQuery source) {
        CompletedTaskQuery query = new CompletedTaskQuery();
        page(source, query); query.setProcessCode(source.getProcessCode()); query.setProcessName(source.getProcessName());
        query.setInstanceTitle(source.getInstanceTitle()); query.setStarterUserId(source.getStarterUserId());
        query.setNodeCode(source.getNodeCode()); query.setActionType(source.getActionType());
        query.setCompletedFrom(source.getFrom()); query.setCompletedTo(source.getTo());
        return taskQueryService.queryCompletedTasks(query);
    }

    /**
     * 查询当前用户发起的实例，避免业务端重复实现流程列表过滤规则。
     */
    public PageResult<ProcessInstanceDTO> started(WorkflowListQuery source) {
        StartedInstanceQuery query = new StartedInstanceQuery();
        page(source, query); query.setProcessCode(source.getProcessCode()); query.setInstanceTitle(source.getInstanceTitle());
        query.setBusinessKey(source.getBusinessKey()); query.setInstanceStatus(source.getStatus());
        query.setCurrentNodeCode(source.getCurrentNodeCode()); query.setStartedFrom(source.getFrom()); query.setStartedTo(source.getTo());
        return taskQueryService.queryStartedInstances(query);
    }

    /**
     * 查询当前用户已阅记录，已阅归属由平台查询服务绑定到当前用户。
     */
    public PageResult<ReadRecordDTO> readRecords(WorkflowListQuery source) {
        ReadRecordQuery query = new ReadRecordQuery();
        page(source, query); query.setInstanceId(source.getInstanceId());
        return taskQueryService.queryReadRecords(query);
    }

    /**
     * 按任务 ID 定位活动任务，用于任务详情入口反查流程实例。
     */
    public TaskDTO getTask(String taskId) { return taskQueryService.getTask(taskId); }

    /**
     * 读取流程实例详情，业务侧只做聚合和脱敏，不复制实例状态算法。
     */
    public ProcessInstanceDetailDTO getInstance(String instanceId) { return runtimeService.getInstance(instanceId); }

    /**
     * 读取流程定义详情，用于组装只读表单字段、节点和连线。
     */
    public ProcessDefinitionDetailDTO getDefinition(String definitionId) { return definitionService.getDefinition(definitionId); }

    /**
     * 查询直送上下文，仅用于前端展示可选动作，最终权限仍由平台动作接口校验。
     */
    public DirectSendContextDTO directSendContext(String taskId) { return runtimeService.getDirectSendContext(taskId); }

    /**
     * Queries platform-validated reject targets for an active task.
     */
    public List<ProcessNodeDTO> rejectTargetNodes(String taskId) {
        return runtimeService.getRejectTargetNodes(taskId);
    }

    /**
     * 查询实例附件元数据；B2/B3 只展示脱敏元数据，上传下载留给后续阶段。
     */
    public List<AttachmentDTO> attachments(String instanceId) {
        AttachmentQuery query = new AttachmentQuery();
        query.setInstanceId(instanceId);
        query.setOperatorUserId(currentUser().getUserId());
        return attachmentService.queryAttachments(query);
    }

    /** 使用当前申请返工任务原子替换已有实例附件。 */
    public AttachmentDTO replaceInstanceAttachment(ReplaceInstanceAttachmentRequest request) {
        return attachmentService.replaceInstanceAttachment(request);
    }

    /**
     * 幂等标记当前用户已阅指定实例。
     */
    public ReadRecordDTO markRead(String instanceId) { return readRecordService.markRead(instanceId); }

    /**
     * 根据 REST 动作名分派到 platform-starter 的唯一对应方法，业务端不自行更新任务状态。
     */
    public TaskActionResult execute(String action, TaskOperationRequest request) {
        if ("approve".equals(action)) return runtimeService.approve((com.flowmind.platform.api.request.ApproveTaskRequest) request);
        if ("submit".equals(action)) return runtimeService.submitTask((com.flowmind.platform.api.request.SubmitTaskRequest) request);
        if ("reject".equals(action)) return runtimeService.reject((com.flowmind.platform.api.request.RejectTaskRequest) request);
        if ("return".equals(action)) return runtimeService.returnToStarter((com.flowmind.platform.api.request.ReturnTaskRequest) request);
        if ("withdraw".equals(action)) return runtimeService.withdraw((com.flowmind.platform.api.request.WithdrawTaskRequest) request);
        if ("direct-send".equals(action)) return runtimeService.directSend((com.flowmind.platform.api.request.DirectSendRequest) request);
        if ("transfer".equals(action)) return runtimeService.transfer((com.flowmind.platform.api.request.TransferTaskRequest) request);
        if ("delegate".equals(action)) return runtimeService.delegateTask((com.flowmind.platform.api.request.DelegateTaskRequest) request);
        if ("add-sign".equals(action)) return runtimeService.addSign((com.flowmind.platform.api.request.AddSignRequest) request);
        if ("claim".equals(action)) return runtimeService.claim((com.flowmind.platform.api.request.ClaimTaskRequest) request);
        if ("unclaim".equals(action)) return runtimeService.unclaim((com.flowmind.platform.api.request.UnclaimTaskRequest) request);
        throw new IllegalArgumentException("unsupported workflow action");
    }

    /**
     * 复制分页参数，默认值和最大页大小由平台查询服务统一规范化。
     */
    private void page(WorkflowListQuery source, com.flowmind.platform.api.dto.PageQuery target) {
        target.setPageNo(source.getPageNo()); target.setPageSize(source.getPageSize());
    }
}

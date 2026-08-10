package com.flowmind.business.platform;

import com.flowmind.business.workflow.dto.WorkflowDetailResponse;
import com.flowmind.business.workflow.dto.WorkflowHistoryTaskResponse;
import com.flowmind.business.workflow.dto.WorkflowInstanceResponse;
import com.flowmind.business.workflow.dto.WorkflowPageResponse;
import com.flowmind.business.workflow.dto.WorkflowReadRecordResponse;
import com.flowmind.business.workflow.dto.WorkflowTaskActionResponse;
import com.flowmind.business.workflow.dto.WorkflowTaskResponse;
import com.flowmind.business.workflow.dto.WorkflowUserResponse;
import com.flowmind.platform.api.dto.AttachmentDTO;
import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessCommentDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessFormFieldDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.ReadRecordDTO;
import com.flowmind.platform.api.dto.TaskActionResult;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.UserContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PlatformDtoMapper {

    /**
     * 将平台当前用户上下文转换为业务侧对前端稳定暴露的用户信息。
     */
    public WorkflowUserResponse user(UserContext source) {
        WorkflowUserResponse target = new WorkflowUserResponse();
        target.setUserId(source.getUserId());
        target.setUserName(source.getUserName());
        target.setDepartmentId(source.getDepartmentId());
        target.setDepartmentName(source.getDepartmentName());
        return target;
    }

    /**
     * 映射活动任务摘要，并保留 taskVersion 供审批动作做乐观锁校验。
     */
    public WorkflowTaskResponse task(TaskDTO source) {
        if (source == null) return null;
        WorkflowTaskResponse target = new WorkflowTaskResponse();
        target.setTaskId(source.getTaskId());
        target.setInstanceId(source.getInstanceId());
        target.setProcessCode(source.getProcessCode());
        target.setProcessName(source.getProcessName());
        target.setInstanceTitle(source.getInstanceTitle());
        target.setStarterUserId(source.getStarterUserId());
        target.setStarterUserName(source.getStarterUserName());
        target.setNodeCode(source.getNodeCode());
        target.setNodeName(source.getNodeName());
        target.setCandidateUserIds(source.getCandidateUserIds());
        target.setAssigneeUserId(source.getAssigneeUserId());
        target.setAssigneeUserName(source.getAssigneeUserName());
        target.setDelegateFromUserId(source.getDelegateFromUserId());
        target.setDelegateFromUserName(source.getDelegateFromUserName());
        target.setTaskStatus(name(source.getTaskStatus()));
        target.setTaskVersion(source.getTaskVersion());
        target.setCreatedAt(source.getCreatedAt());
        target.setDueAt(source.getDueAt());
        return target;
    }

    /**
     * 映射历史任务摘要，只暴露办理展示字段，不返回内部流转快照。
     */
    public WorkflowHistoryTaskResponse history(HistoryTaskDTO source) {
        WorkflowHistoryTaskResponse target = new WorkflowHistoryTaskResponse();
        target.setHistoryTaskId(source.getHistoryTaskId());
        target.setInstanceId(source.getInstanceId());
        target.setActiveTaskId(source.getActiveTaskId());
        target.setProcessCode(source.getProcessCode());
        target.setProcessName(source.getProcessName());
        target.setInstanceTitle(source.getInstanceTitle());
        target.setNodeCode(source.getNodeCode());
        target.setNodeName(source.getNodeName());
        target.setAssigneeUserId(source.getAssigneeUserId());
        target.setAssigneeUserName(source.getAssigneeUserName());
        target.setDelegateFromUserId(source.getDelegateFromUserId());
        target.setDelegateFromUserName(source.getDelegateFromUserName());
        target.setHandleType(name(source.getHandleType()));
        target.setActionType(name(source.getActionType()));
        target.setComment(source.getComment());
        target.setStartedAt(source.getStartedAt());
        target.setCompletedAt(source.getCompletedAt());
        return target;
    }

    /**
     * 映射实例摘要，并按流程定义声明的字段白名单过滤业务变量。
     */
    public WorkflowInstanceResponse instance(ProcessInstanceDTO source, Set<String> visibleVariables) {
        WorkflowInstanceResponse target = new WorkflowInstanceResponse();
        target.setInstanceId(source.getInstanceId());
        target.setDefinitionId(source.getDefinitionId());
        target.setProcessCode(source.getProcessCode());
        target.setProcessName(source.getProcessName());
        target.setVersion(source.getVersion());
        target.setInstanceTitle(source.getInstanceTitle());
        target.setStarterUserId(source.getStarterUserId());
        target.setStarterUserName(source.getStarterUserName());
        target.setStarterDepartmentId(source.getStarterDeptId());
        target.setInstanceStatus(name(source.getInstanceStatus()));
        target.setCurrentNodeCodes(source.getCurrentNodeCodes());
        target.setVariables(visibleVariables(source.getVariables(), visibleVariables));
        target.setStartedAt(source.getStartedAt());
        target.setEndedAt(source.getEndedAt());
        return target;
    }

    /**
     * 映射待办分页结果。
     */
    public WorkflowPageResponse<WorkflowTaskResponse> taskPage(PageResult<TaskDTO> source) {
        List<WorkflowTaskResponse> records = new ArrayList<WorkflowTaskResponse>();
        for (TaskDTO item : safe(source.getRecords())) records.add(task(item));
        return page(source, records);
    }

    /**
     * 映射已办分页结果。
     */
    public WorkflowPageResponse<WorkflowHistoryTaskResponse> historyPage(PageResult<HistoryTaskDTO> source) {
        List<WorkflowHistoryTaskResponse> records = new ArrayList<WorkflowHistoryTaskResponse>();
        for (HistoryTaskDTO item : safe(source.getRecords())) records.add(history(item));
        return page(source, records);
    }

    /**
     * 映射我发起的实例分页结果，列表场景不展开业务变量。
     */
    public WorkflowPageResponse<WorkflowInstanceResponse> instancePage(PageResult<ProcessInstanceDTO> source) {
        List<WorkflowInstanceResponse> records = new ArrayList<WorkflowInstanceResponse>();
        for (ProcessInstanceDTO item : safe(source.getRecords())) records.add(instance(item, Collections.<String>emptySet()));
        return page(source, records);
    }

    /**
     * 映射已阅分页结果，并补充实例标题、流程名称和实例状态等展示信息。
     */
    public WorkflowPageResponse<WorkflowReadRecordResponse> readPage(PageResult<ReadRecordDTO> source,
                                                                     Map<String, ProcessInstanceDTO> instances) {
        List<WorkflowReadRecordResponse> records = new ArrayList<WorkflowReadRecordResponse>();
        for (ReadRecordDTO item : safe(source.getRecords())) {
            WorkflowReadRecordResponse target = new WorkflowReadRecordResponse();
            target.setReadRecordId(item.getReadRecordId());
            target.setInstanceId(item.getInstanceId());
            target.setTaskId(item.getTaskId());
            target.setReadAt(item.getReadAt());
            ProcessInstanceDTO instance = instances.get(item.getInstanceId());
            if (instance != null) {
                target.setProcessCode(instance.getProcessCode());
                target.setProcessName(instance.getProcessName());
                target.setInstanceTitle(instance.getInstanceTitle());
                target.setInstanceStatus(name(instance.getInstanceStatus()));
            }
            records.add(target);
        }
        return page(source, records);
    }

    /**
     * 聚合实例详情响应，统一完成字段排序、变量白名单、附件脱敏和动作列表映射。
     */
    public WorkflowDetailResponse detail(ProcessInstanceDTO instance,
                                           ProcessDefinitionDetailDTO definition,
                                           TaskDTO currentTask,
                                           List<TaskDTO> activeTasks,
                                           List<HistoryTaskDTO> historyTasks,
                                           List<ProcessCommentDTO> comments,
                                           List<AttachmentDTO> attachments,
                                           List<String> allowedActions) {
        WorkflowDetailResponse target = new WorkflowDetailResponse();
        List<ProcessFormFieldDTO> fields = new ArrayList<ProcessFormFieldDTO>(safe(definition.getFormFields()));
        Collections.sort(fields, Comparator.comparing(ProcessFormFieldDTO::getSortOrder,
                Comparator.nullsLast(Integer::compareTo)).thenComparing(ProcessFormFieldDTO::getFieldCode,
                Comparator.nullsLast(String::compareTo)));
        Set<String> fieldCodes = new LinkedHashSet<String>();
        List<WorkflowDetailResponse.FormFieldView> fieldViews = new ArrayList<WorkflowDetailResponse.FormFieldView>();
        for (ProcessFormFieldDTO field : fields) {
            fieldCodes.add(field.getFieldCode());
            WorkflowDetailResponse.FormFieldView view = new WorkflowDetailResponse.FormFieldView();
            view.setFieldCode(field.getFieldCode()); view.setFieldName(field.getFieldName());
            view.setFieldType(field.getFieldType()); view.setControlType(field.getControlType());
            view.setRequired(field.getRequired()); view.setValidationRule(field.getValidationRule());
            view.setSortOrder(field.getSortOrder()); fieldViews.add(view);
        }
        target.setInstance(instance(instance, fieldCodes));
        WorkflowDetailResponse.DefinitionView definitionView = new WorkflowDetailResponse.DefinitionView();
        definitionView.setProcessCode(definition.getProcessCode());
        definitionView.setProcessName(definition.getProcessName());
        definitionView.setVersion(definition.getVersion());
        target.setDefinition(definitionView);
        target.setFormFields(fieldViews);
        target.setNodes(nodes(definition.getNodes()));
        target.setEdges(edges(definition.getEdges()));
        target.setCurrentTask(task(currentTask));
        target.setActiveTasks(tasks(activeTasks));
        target.setHistoryTasks(histories(historyTasks));
        target.setComments(comments(comments));
        target.setAttachments(attachments(attachments));
        target.setAllowedActions(allowedActions == null ? new ArrayList<String>() : allowedActions);
        return target;
    }

    /**
     * 映射平台动作执行结果，只返回前端需要的实例摘要、归档任务、新增任务和更新任务。
     */
    public WorkflowTaskActionResponse action(TaskActionResult source) {
        WorkflowTaskActionResponse target = new WorkflowTaskActionResponse();
        target.setOperationId(source.getOperationId());
        target.setInstance(instance(source.getInstance(), Collections.<String>emptySet()));
        target.setArchivedTasks(histories(source.getArchivedTasks()));
        target.setCreatedTasks(tasks(source.getCreatedTasks()));
        target.setUpdatedTasks(tasks(source.getUpdatedTasks()));
        target.setReplayed(source.isReplayed());
        return target;
    }

    /**
     * 批量映射活动任务列表。
     */
    private List<WorkflowTaskResponse> tasks(List<TaskDTO> sources) {
        List<WorkflowTaskResponse> targets = new ArrayList<WorkflowTaskResponse>();
        for (TaskDTO source : safe(sources)) targets.add(task(source));
        return targets;
    }

    /**
     * 批量映射历史任务列表。
     */
    private List<WorkflowHistoryTaskResponse> histories(List<HistoryTaskDTO> sources) {
        List<WorkflowHistoryTaskResponse> targets = new ArrayList<WorkflowHistoryTaskResponse>();
        for (HistoryTaskDTO source : safe(sources)) targets.add(history(source));
        return targets;
    }

    /**
     * 按流程定义排序号稳定输出节点视图，方便前端画流程图。
     */
    private List<WorkflowDetailResponse.NodeView> nodes(List<ProcessNodeDTO> sources) {
        List<ProcessNodeDTO> sorted = new ArrayList<ProcessNodeDTO>(safe(sources));
        Collections.sort(sorted, Comparator.comparing(ProcessNodeDTO::getSortOrder,
                Comparator.nullsLast(Integer::compareTo)));
        List<WorkflowDetailResponse.NodeView> targets = new ArrayList<WorkflowDetailResponse.NodeView>();
        for (ProcessNodeDTO source : sorted) {
            WorkflowDetailResponse.NodeView target = new WorkflowDetailResponse.NodeView();
            target.setNodeCode(source.getNodeCode()); target.setNodeName(source.getNodeName());
            target.setNodeType(name(source.getNodeType())); target.setPositionX(source.getPositionX());
            target.setPositionY(source.getPositionY()); target.setSortOrder(source.getSortOrder());
            targets.add(target);
        }
        return targets;
    }

    /**
     * 按流程定义排序号稳定输出连线视图。
     */
    private List<WorkflowDetailResponse.EdgeView> edges(List<ProcessEdgeDTO> sources) {
        List<ProcessEdgeDTO> sorted = new ArrayList<ProcessEdgeDTO>(safe(sources));
        Collections.sort(sorted, Comparator.comparing(ProcessEdgeDTO::getSortOrder,
                Comparator.nullsLast(Integer::compareTo)));
        List<WorkflowDetailResponse.EdgeView> targets = new ArrayList<WorkflowDetailResponse.EdgeView>();
        for (ProcessEdgeDTO source : sorted) {
            WorkflowDetailResponse.EdgeView target = new WorkflowDetailResponse.EdgeView();
            target.setEdgeCode(source.getEdgeCode()); target.setSourceNodeCode(source.getSourceNodeCode());
            target.setTargetNodeCode(source.getTargetNodeCode()); target.setDefaultEdge(source.getDefaultEdge());
            target.setSortOrder(source.getSortOrder()); targets.add(target);
        }
        return targets;
    }

    /**
     * 映射审批意见列表，保留展示所需的节点、人员和时间信息。
     */
    private List<WorkflowDetailResponse.CommentView> comments(List<ProcessCommentDTO> sources) {
        List<WorkflowDetailResponse.CommentView> targets = new ArrayList<WorkflowDetailResponse.CommentView>();
        for (ProcessCommentDTO source : safe(sources)) {
            WorkflowDetailResponse.CommentView target = new WorkflowDetailResponse.CommentView();
            target.setCommentId(source.getCommentId()); target.setTaskId(source.getTaskId());
            target.setNodeCode(source.getNodeCode()); target.setOperatorUserId(source.getOperatorUserId());
            target.setOperatorUserName(source.getOperatorUserName()); target.setComment(source.getComment());
            target.setCreatedAt(source.getCreatedAt()); targets.add(target);
        }
        return targets;
    }

    /**
     * 映射附件元数据，并过滤已删除附件和底层存储键。
     */
    private List<WorkflowDetailResponse.AttachmentView> attachments(List<AttachmentDTO> sources) {
        List<WorkflowDetailResponse.AttachmentView> targets = new ArrayList<WorkflowDetailResponse.AttachmentView>();
        for (AttachmentDTO source : safe(sources)) {
            if (Boolean.TRUE.equals(source.getDeleted())) continue;
            WorkflowDetailResponse.AttachmentView target = new WorkflowDetailResponse.AttachmentView();
            target.setAttachmentId(source.getAttachmentId()); target.setInstanceId(source.getInstanceId());
            target.setTaskId(source.getTaskId()); target.setOwnerType(name(source.getOwnerType()));
            target.setAttachmentCode(source.getAttachmentCode()); target.setFieldCode(source.getFieldCode());
            target.setFileName(source.getFileName()); target.setContentType(source.getContentType());
            target.setSizeBytes(source.getSizeBytes()); target.setUploadedBy(source.getUploadedBy());
            target.setUploadedAt(source.getUploadedAt()); targets.add(target);
        }
        return targets;
    }

    /**
     * 只返回流程定义表单字段声明过的业务变量。
     */
    private Map<String, Object> visibleVariables(Map<String, Object> source, Set<String> allowed) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (source == null || allowed == null) return result;
        for (String key : allowed) if (source.containsKey(key)) result.put(key, source.get(key));
        return result;
    }

    /**
     * 复用平台分页元数据，替换 records 为业务侧响应模型。
     */
    private <S, T> WorkflowPageResponse<T> page(PageResult<S> source, List<T> records) {
        WorkflowPageResponse<T> target = new WorkflowPageResponse<T>();
        target.setRecords(records); target.setPageNo(source.getPageNo()); target.setPageSize(source.getPageSize());
        target.setTotal(source.getTotal()); target.setTotalPages(source.getTotalPages());
        return target;
    }

    /**
     * 将枚举统一映射为字符串，避免前端依赖平台枚举类型。
     */
    private String name(Enum<?> value) { return value == null ? null : value.name(); }

    /**
     * 空集合保护，简化聚合映射时的空值处理。
     */
    private <T> List<T> safe(List<T> values) { return values == null ? Collections.<T>emptyList() : values; }
}

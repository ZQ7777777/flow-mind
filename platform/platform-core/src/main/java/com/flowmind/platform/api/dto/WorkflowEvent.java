package com.flowmind.platform.api.dto;

import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.WorkflowEventTypeEnum;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class WorkflowEvent {

    /** 事件 ID。 */
    private String eventId;
    /** 触发事件的操作幂等号。 */
    private String operationId;
    /** 回调事件类型。 */
    private WorkflowEventTypeEnum eventType;
    /** 流程编码。 */
    private String processCode;
    /** 流程实例 ID。 */
    private String instanceId;
    /** 触发事件的动作类型。 */
    private ActionTypeEnum actionType;
    /** 操作人快照。 */
    private UserContext operator;
    /** 本次动作归档或取消的历史任务。 */
    private List<HistoryTaskDTO> archivedTasks;
    /** 本次动作新建的活动任务。 */
    private List<TaskDTO> createdTasks;
    /** 事件发生后的流程变量快照。 */
    private Map<String, Object> variables;
    /** 事件发生时间。 */
    private LocalDateTime occurredAt;

    public WorkflowEvent() {
    }

    public WorkflowEvent(String eventId, String operationId, WorkflowEventTypeEnum eventType, String processCode,
                         String instanceId, ActionTypeEnum actionType, UserContext operator,
                         List<HistoryTaskDTO> archivedTasks, List<TaskDTO> createdTasks,
                         Map<String, Object> variables, LocalDateTime occurredAt) {
        this.eventId = eventId;
        this.operationId = operationId;
        this.eventType = eventType;
        this.processCode = processCode;
        this.instanceId = instanceId;
        this.actionType = actionType;
        this.operator = operator;
        this.archivedTasks = archivedTasks;
        this.createdTasks = createdTasks;
        this.variables = variables;
        this.occurredAt = occurredAt;
    }

    public WorkflowEvent(String eventId, String operationId, String eventType, String processCode, String instanceId,
                         ActionTypeEnum actionType, UserContext operator, List<HistoryTaskDTO> archivedTasks,
                         List<TaskDTO> createdTasks, Map<String, Object> variables, LocalDateTime occurredAt) {
        this(eventId, operationId, WorkflowEventTypeEnum.fromCode(eventType), processCode, instanceId, actionType,
                operator, archivedTasks, createdTasks, variables, occurredAt);
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public WorkflowEventTypeEnum getEventType() {
        return eventType;
    }

    public void setEventType(WorkflowEventTypeEnum eventType) {
        this.eventType = eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = WorkflowEventTypeEnum.fromCode(eventType);
    }

    public String getEventTypeCode() {
        return eventType == null ? null : eventType.name();
    }

    public String getProcessCode() {
        return processCode;
    }

    public void setProcessCode(String processCode) {
        this.processCode = processCode;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public ActionTypeEnum getActionType() {
        return actionType;
    }

    public void setActionType(ActionTypeEnum actionType) {
        this.actionType = actionType;
    }

    public UserContext getOperator() {
        return operator;
    }

    public void setOperator(UserContext operator) {
        this.operator = operator;
    }

    public List<HistoryTaskDTO> getArchivedTasks() {
        return archivedTasks;
    }

    public void setArchivedTasks(List<HistoryTaskDTO> archivedTasks) {
        this.archivedTasks = archivedTasks;
    }

    public List<TaskDTO> getCreatedTasks() {
        return createdTasks;
    }

    public void setCreatedTasks(List<TaskDTO> createdTasks) {
        this.createdTasks = createdTasks;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }
}

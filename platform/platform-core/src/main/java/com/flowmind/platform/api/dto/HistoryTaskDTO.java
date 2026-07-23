package com.flowmind.platform.api.dto;

import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.HandleTypeEnum;

import java.time.LocalDateTime;
import java.util.Map;

public class HistoryTaskDTO {

    /** 历史任务记录 ID。 */
    private String historyTaskId;
    /** 历史任务所属的流程实例 ID。 */
    private String instanceId;
    /** 流程定义的业务编码快照。 */
    private String processCode;
    /** 流程名称快照。 */
    private String processName;
    /** 流程实例标题。 */
    private String instanceTitle;
    /** 流程发起人的用户 ID。 */
    private String starterUserId;
    /** 流程发起人的名称快照。 */
    private String starterUserName;
    /** 触发该历史记录的操作幂等号。 */
    private String operationId;
    /** 被归档或取消的原活动任务 ID。 */
    private String activeTaskId;
    /** 任务所在流程节点的编码。 */
    private String nodeCode;
    /** 任务所在流程节点的名称。 */
    private String nodeName;
    /** 会签、或签或并行任务组 ID；非分组任务可为空。 */
    private String taskGroupId;
    /** 并行任务所在分支的标识；非并行任务可为空。 */
    private String branchKey;
    /** 实际办理人的用户 ID。 */
    private String assigneeUserId;
    /** 实际办理人的名称快照。 */
    private String assigneeUserName;
    /** 委托来源用户的 ID；非委托办理可为空。 */
    private String delegateFromUserId;
    /** 委托来源用户的名称快照；非委托办理可为空。 */
    private String delegateFromUserName;
    /** 历史任务的实际办理方式。 */
    private HandleTypeEnum handleType;
    /** 归档该任务的流程动作类型。 */
    private ActionTypeEnum actionType;
    /** 办理时填写的意见或操作说明。 */
    private String comment;
    /** 办理完成时固化的流程变量快照。 */
    private Map<String, Object> variablesSnapshot;
    /** 该任务开始办理或创建的时间。 */
    private LocalDateTime startedAt;
    /** 该任务归档或完成的时间。 */
    private LocalDateTime completedAt;

    public HistoryTaskDTO() {
    }

    public String getHistoryTaskId() {
        return historyTaskId;
    }

    public void setHistoryTaskId(String historyTaskId) {
        this.historyTaskId = historyTaskId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getProcessCode() {
        return processCode;
    }

    public void setProcessCode(String processCode) {
        this.processCode = processCode;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public String getInstanceTitle() {
        return instanceTitle;
    }

    public void setInstanceTitle(String instanceTitle) {
        this.instanceTitle = instanceTitle;
    }

    public String getStarterUserId() {
        return starterUserId;
    }

    public void setStarterUserId(String starterUserId) {
        this.starterUserId = starterUserId;
    }

    public String getStarterUserName() {
        return starterUserName;
    }

    public void setStarterUserName(String starterUserName) {
        this.starterUserName = starterUserName;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getActiveTaskId() {
        return activeTaskId;
    }

    public void setActiveTaskId(String activeTaskId) {
        this.activeTaskId = activeTaskId;
    }

    public String getNodeCode() {
        return nodeCode;
    }

    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getTaskGroupId() {
        return taskGroupId;
    }

    public void setTaskGroupId(String taskGroupId) {
        this.taskGroupId = taskGroupId;
    }

    public String getBranchKey() {
        return branchKey;
    }

    public void setBranchKey(String branchKey) {
        this.branchKey = branchKey;
    }

    public String getAssigneeUserId() {
        return assigneeUserId;
    }

    public void setAssigneeUserId(String assigneeUserId) {
        this.assigneeUserId = assigneeUserId;
    }

    public String getAssigneeUserName() {
        return assigneeUserName;
    }

    public void setAssigneeUserName(String assigneeUserName) {
        this.assigneeUserName = assigneeUserName;
    }

    public String getDelegateFromUserId() {
        return delegateFromUserId;
    }

    public void setDelegateFromUserId(String delegateFromUserId) {
        this.delegateFromUserId = delegateFromUserId;
    }

    public String getDelegateFromUserName() {
        return delegateFromUserName;
    }

    public void setDelegateFromUserName(String delegateFromUserName) {
        this.delegateFromUserName = delegateFromUserName;
    }

    public HandleTypeEnum getHandleType() {
        return handleType;
    }

    public void setHandleType(HandleTypeEnum handleType) {
        this.handleType = handleType;
    }

    public ActionTypeEnum getActionType() {
        return actionType;
    }

    public void setActionType(ActionTypeEnum actionType) {
        this.actionType = actionType;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Map<String, Object> getVariablesSnapshot() {
        return variablesSnapshot;
    }

    public void setVariablesSnapshot(Map<String, Object> variablesSnapshot) {
        this.variablesSnapshot = variablesSnapshot;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}

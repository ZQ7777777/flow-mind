package com.flowmind.platform.api.entity.dto;

import java.time.LocalDateTime;
import java.util.List;

public class TaskDTO {

    /** 活动任务 ID。 */
    private String taskId;
    /** 任务所属的流程实例 ID。 */
    private String instanceId;
    /** 任务所属流程定义的 ID。 */
    private String definitionId;
    /** 任务所在用户节点的编码。 */
    private String nodeCode;
    /** 任务所在用户节点的名称。 */
    private String nodeName;
    /** 可认领或可办理该任务的候选用户 ID 列表。 */
    private List<String> candidateUserIds;
    /** 当前办理人或认领人的用户 ID。 */
    private String assigneeUserId;
    /** 当前办理人或认领人的名称快照。 */
    private String assigneeUserName;
    /** 委托来源用户的 ID；非委托任务可为空。 */
    private String delegateFromUserId;
    /** 委托来源用户的名称快照；非委托任务可为空。 */
    private String delegateFromUserName;
    /** 会签、或签或并行任务组 ID；非分组任务可为空。 */
    private String taskGroupId;
    /** 并行任务所在分支的标识；非并行任务可为空。 */
    private String branchKey;
    /** 活动任务的乐观锁版本，提交任务级请求时原样传入 expectedTaskVersion。 */
    private Long taskVersion;
    /** 活动任务创建时间。 */
    private LocalDateTime createdAt;
    /** 节点配置计算出的任务超时时间；未配置时可为空。 */
    private LocalDateTime dueAt;

    public TaskDTO() {
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(String definitionId) {
        this.definitionId = definitionId;
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

    public List<String> getCandidateUserIds() {
        return candidateUserIds;
    }

    public void setCandidateUserIds(List<String> candidateUserIds) {
        this.candidateUserIds = candidateUserIds;
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

    public Long getTaskVersion() {
        return taskVersion;
    }

    public void setTaskVersion(Long taskVersion) {
        this.taskVersion = taskVersion;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getDueAt() {
        return dueAt;
    }

    public void setDueAt(LocalDateTime dueAt) {
        this.dueAt = dueAt;
    }
}

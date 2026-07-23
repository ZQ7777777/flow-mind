package com.flowmind.platform.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 历史任务查询读模型，承载历史任务及其流程实例、节点展示字段。
 *
 * @author FlowMind
 * @since 2026-07-23
 */
@Data
public class HistoryTaskQueryEntity {
    /** 历史任务 ID。 */
    private String historyTaskId;
    /** 流程实例 ID。 */
    private String instanceId;
    /** 流程编码快照。 */
    private String processCode;
    /** 流程名称快照。 */
    private String processName;
    /** 实例标题。 */
    private String instanceTitle;
    /** 发起人用户 ID。 */
    private String starterUserId;
    /** 发起人名称快照。 */
    private String starterUserName;
    /** 操作幂等号。 */
    private String operationId;
    /** 原活动任务 ID。 */
    private String activeTaskId;
    /** 节点编码。 */
    private String nodeCode;
    /** 节点名称。 */
    private String nodeName;
    /** 任务组 ID。 */
    private String taskGroupId;
    /** 分支键。 */
    private String branchKey;
    /** 实际办理人 ID。 */
    private String assigneeUserId;
    /** 实际办理人名称。 */
    private String assigneeUserName;
    /** 委托来源用户 ID。 */
    private String delegateFromUserId;
    /** 委托来源用户名称。 */
    private String delegateFromUserName;
    /** 办理方式。 */
    private String handleType;
    /** 动作类型。 */
    private String actionType;
    /** 审批意见。 */
    private String commentText;
    /** 变量快照 JSON。 */
    private String variablesSnapshot;
    /** 任务开始时间。 */
    private LocalDateTime startedAt;
    /** 任务完成时间。 */
    private LocalDateTime completedAt;
}

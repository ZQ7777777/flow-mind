package com.flowmind.platform.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务查询读模型，承载活动任务及其流程实例、节点展示字段。
 *
 * @author FlowMind
 * @since 2026-07-23
 */
@Data
public class TaskQueryEntity {
    /** 活动任务 ID。 */
    private String taskId;
    /** 流程实例 ID。 */
    private String instanceId;
    /** 流程定义 ID。 */
    private String definitionId;
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
    /** 当前节点编码。 */
    private String nodeCode;
    /** 当前节点名称。 */
    private String nodeName;
    /** 候选用户 ID JSON 数组。 */
    private String candidateUserIds;
    /** 当前办理人或认领人 ID。 */
    private String assigneeUserId;
    /** 当前办理人名称快照。 */
    private String assigneeUserName;
    /** 委托来源用户 ID。 */
    private String delegateFromUserId;
    /** 委托来源用户名称。 */
    private String delegateFromUserName;
    /** 任务组 ID。 */
    private String taskGroupId;
    /** 分支键。 */
    private String branchKey;
    /** 活动任务状态。 */
    private String taskStatus;
    /** 乐观锁版本。 */
    private Long lockVersion;
    /** 任务创建时间。 */
    private LocalDateTime createdAt;
    /** 任务到期时间。 */
    private LocalDateTime dueAt;
}

package com.flowmind.platform.api.dto;

import com.flowmind.platform.api.enums.TaskStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动任务返回对象，承载任务定位、办理人快照和乐观锁版本等运行时信息。
 *
 * @author Intern B
 * @since 2026-07-15
 */
@Data
public class TaskDTO {

    /** 活动任务 ID。 */
    private String taskId;
    /** 任务所属的流程实例 ID。 */
    private String instanceId;
    /** 任务所属流程定义的 ID。 */
    private String definitionId;
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
    /** 活动任务当前状态。 */
    private TaskStatusEnum taskStatus;
    /** 活动任务的乐观锁版本，提交任务级请求时原样传入 expectedTaskVersion。 */
    private Long taskVersion;
    /** 活动任务创建时间。 */
    private LocalDateTime createdAt;
    /** 节点配置计算出的任务超时时间；未配置时可为空。 */
    private LocalDateTime dueAt;
}

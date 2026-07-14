package com.flowmind.platform.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 活动任务返回对象，描述待办任务的实例、节点、候选人、办理人和乐观锁版本。
 *
 * @author Yuxin Xu
 * @since 2026-07-14
 */
@Data
public class TaskDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 活动任务 ID。
     */
    private String taskId;
    /**
     * 所属流程实例 ID。
     */
    private String instanceId;
    /**
     * 所属流程定义 ID。
     */
    private String definitionId;
    /**
     * 流程定义版本快照。
     */
    private Integer version;
    /**
     * 当前任务所在节点编码。
     */
    private String nodeCode;
    /**
     * 当前任务所在节点名称快照。
     */
    private String nodeName;
    /**
     * 候选办理人 ID 列表。
     */
    private List<String> candidateUserIds = new ArrayList<>();
    /**
     * 当前办理人或认领人 ID。
     */
    private String assigneeUserId;
    /**
     * 当前办理人或认领人名称快照。
     */
    private String assigneeUserName;
    /**
     * 委托来源用户 ID。
     */
    private String delegateFromUserId;
    /**
     * 任务状态，如 ACTIVE、CLAIMED、COMPLETED、CANCELED。
     */
    private String taskStatus;
    /**
     * 会签、或签或并行任务组 ID。
     */
    private String taskGroupId;
    /**
     * 并行分支标识，可为空。
     */
    private String branchKey;
    /**
     * 任务乐观锁版本，对应 process_active_task.lock_version。
     */
    private Long taskVersion;
    /**
     * 任务创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 任务超时时间。
     */
    private LocalDateTime dueAt;
}

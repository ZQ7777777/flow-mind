package com.flowmind.platform.persistence.entity;
import lombok.Data;
import java.time.LocalDateTime;
/** 活动任务表 process_active_task 的持久化实体。
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 **/
@Data
public class ProcessActiveTaskEntity {
    /**
     * 活动任务主键。
     */
    private String id;
    /**
     * 所属流程实例 ID。
     */
    private String instanceId;
    /**
     * 所属流程定义 ID。
     */
    private String definitionId;
    /**
     * 当前用户任务节点编码。
     */
    private String nodeCode;
    /**
     * 候选用户 ID JSON 数组。
     */
    private String candidateUserIds;
    /**
     * 当前办理人或认领人 ID。
     */
    private String assigneeUserId;
    /**
     * 当前办理人名称快照。
     */
    private String assigneeUserName;
    /**
     * 委托来源用户 ID。
     */
    private String delegateFromUserId;
    /**
     * Runtime-only delegate source user name snapshot.
     */
    private String delegateFromUserName;
    /**
     * 任务状态，典型值：ACTIVE、CLAIMED、COMPLETED、CANCELED。
     */
    private String taskStatus;
    /**
     * 会签、或签或并行任务组 ID。
     */
    private String taskGroupId;
    /**
     * 并行分支键，通常为出线 edge_code。
     */
    private String branchKey;
    /**
     * 乐观锁版本，查询后映射为 TaskDTO.taskVersion。
     */
    private Long lockVersion;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 任务超时时间。
     */
    private LocalDateTime dueAt;
}

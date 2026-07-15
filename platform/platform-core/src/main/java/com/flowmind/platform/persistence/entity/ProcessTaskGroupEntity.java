package com.flowmind.platform.persistence.entity;
import lombok.Data;
import java.time.LocalDateTime;
/** 任务组表 process_task_group 的持久化实体。
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 **/
@Data
public class ProcessTaskGroupEntity {
    /**
     * 任务组主键。
     */
    private String id;
    /**
     * 所属流程实例 ID。
     */
    private String instanceId;
    /**
     * 会签、或签节点或并行分支网关编码。
     */
    private String nodeCode;
    /**
     * 配对的并行汇聚网关编码。
     */
    private String joinNodeCode;
    /**
     * 外层并行任务组 ID。
     */
    private String parentGroupId;
    /**
     * 当前组在外层任务组中的分支键。
     */
    private String parentBranchKey;
    /**
     * 任务组类型，典型值：OR_SIGN、COUNTERSIGN、PARALLEL_GATEWAY。
     */
    private String groupType;
    /**
     * 总任务数或总分支数。
     */
    private Integer totalCount;
    /**
     * 已完成任务数或已到达分支数。
     */
    private Integer completedCount;
    /**
     * 分支状态 JSON 对象，键为 edge_code，值为 RUNNING、ARRIVED 或 CANCELED。
     */
    private String branchStateJson;
    /**
     * 任务组状态，典型值：ACTIVE、COMPLETED、CANCELED。
     */
    private String groupStatus;
    /**
     * 乐观锁版本，初始值为 0。
     */
    private Long lockVersion;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 完成时间。
     */
    private LocalDateTime completedAt;
}

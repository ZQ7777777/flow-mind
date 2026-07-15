package com.flowmind.platform.persistence.entity;
import lombok.Data;
import java.time.LocalDateTime;
/** 历史任务表 process_history_task 的持久化实体。
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 **/
@Data
public class ProcessHistoryTaskEntity {
    /**
     * 历史任务主键。
     */
    private String id;
    /**
     * 所属流程实例 ID。
     */
    private String instanceId;
    /**
     * 产生该历史记录的操作幂等号。
     */
    private String operationId;
    /**
     * 原活动任务 ID。
     */
    private String activeTaskId;
    /**
     * 办理节点编码。
     */
    private String nodeCode;
    /**
     * 关联任务组 ID。
     */
    private String taskGroupId;
    /**
     * 并行分支键。
     */
    private String branchKey;
    /**
     * 实际办理人 ID。
     */
    private String assigneeUserId;
    /**
     * 实际办理人名称快照。
     */
    private String assigneeUserName;
    /**
     * 委托来源用户 ID。
     */
    private String delegateFromUserId;
    /**
     * 委托来源用户名称快照。
     */
    private String delegateFromUserName;
    /**
     * 办理方式，典型值：NORMAL、DELEGATE、TRANSFER、ADMIN_PROXY。
     */
    private String handleType;
    /**
     * 动作类型，典型值：SEND、APPROVE、REJECT、RETURN、TRANSFER、CANCEL。
     */
    private String actionType;
    /**
     * 审批意见或办理说明。
     */
    private String commentText;
    /**
     * 办理时流程变量快照 JSON 对象。
     */
    private String variablesSnapshot;
    /**
     * 任务开始时间。
     */
    private LocalDateTime startedAt;
    /**
     * 任务完成时间。
     */
    private LocalDateTime completedAt;
    /**
     * 扩展信息 JSON 对象，用于保存动作附加上下文。
     */
    private String extraJson;
}

package com.flowmind.platform.persistence.entity;
import lombok.Data;
import java.time.LocalDateTime;
/** 回调日志表 process_callback_log 的持久化实体。
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 **/
@Data
public class ProcessCallbackLogEntity {
    /**
     * 回调日志主键。
     */
    private String id;
    /**
     * 全局唯一且可稳定重建的事件 ID。
     */
    private String eventId;
    /**
     * 关联流程实例 ID。
     */
    private String instanceId;
    /**
     * 关联操作幂等号。
     */
    private String operationId;
    /**
     * 回调事件类型，如 PROCESS_STARTED、TASK_COMPLETED、TASK_CREATED。
     */
    private String eventType;
    /**
     * 触发事件的动作类型，典型值：START、SEND、APPROVE、REJECT。
     */
    private String actionType;
    /**
     * 回调负载 JSON 对象，包含实例、归档任务和新建任务信息。
     */
    private String payloadJson;
    /**
     * 回调状态，典型值：PENDING、SUCCESS、FAILED。
     */
    private String callbackStatus;
    /**
     * 已执行的重试次数。
     */
    private Integer retryCount;
    /**
     * 最近一次失败信息。
     */
    private String lastError;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;
}

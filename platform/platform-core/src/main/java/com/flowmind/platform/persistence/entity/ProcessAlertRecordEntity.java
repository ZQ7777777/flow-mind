package com.flowmind.platform.persistence.entity;
import lombok.Data;
import java.time.LocalDateTime;
/** 异常告警表 process_alert_record 的持久化实体。
 *
 * @author Yuxin Xu
 * @since 2026-07-15 */
@Data
public class ProcessAlertRecordEntity {
    /**
     * 告警记录主键。
     */
    private String id;
    /**
     * 关联流程实例 ID。
     */
    private String instanceId;
    /**
     * 关联活动任务 ID。
     */
    private String taskId;
    /**
     * 告警类型，典型值：TASK_TIMEOUT、CALLBACK_FAILED、ACTION_EXCEPTION。
     */
    private String alertType;
    /**
     * 严重程度，典型值：LOW、MEDIUM、HIGH。
     */
    private String severity;
    /**
     * 告警状态，典型值：OPEN、HANDLED、IGNORED。
     */
    private String alertStatus;
    /**
     * 告警详情 JSON 对象，包含异常原因和运行上下文。
     */
    private String detailJson;
    /**
     * 处理人 ID。
     */
    private String handledBy;
    /**
     * 处理时间。
     */
    private LocalDateTime handledAt;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
}

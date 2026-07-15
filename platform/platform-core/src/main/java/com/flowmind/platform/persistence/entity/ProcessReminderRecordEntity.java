package com.flowmind.platform.persistence.entity;
import lombok.Data;
import java.time.LocalDateTime;
/** 提醒记录表 process_reminder_record 的持久化实体。
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 **/
@Data
public class ProcessReminderRecordEntity {
    /**
     * 提醒记录主键。
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
     * 提醒类型，典型值：MANUAL、AUTO、TIMEOUT。
     */
    private String reminderType;
    /**
     * 目标用户 ID JSON 数组。
     */
    private String targetUserIds;
    /**
     * 提醒消息正文。
     */
    private String message;
    /**
     * 提醒状态，典型值：PENDING、SENT、FAILED。
     */
    private String reminderStatus;
    /**
     * 发送失败信息。
     */
    private String errorMessage;
    /**
     * 创建人 ID。
     */
    private String createdBy;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 实际发送时间。
     */
    private LocalDateTime sentAt;
}

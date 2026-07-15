package com.flowmind.platform.persistence.entity;
import lombok.Data;
import java.time.LocalDateTime;
/** 已阅记录表 process_read_record 的持久化实体。
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 **/
@Data
public class ProcessReadRecordEntity {
    /**
     * 已阅记录主键。
     */
    private String id;
    /**
     * 所属流程实例 ID。
     */
    private String instanceId;
    /**
     * 已阅用户 ID。
     */
    private String userId;
    /**
     * 已阅用户名称快照。
     */
    private String userName;
    /**
     * 阅读时间。
     */
    private LocalDateTime readAt;
}

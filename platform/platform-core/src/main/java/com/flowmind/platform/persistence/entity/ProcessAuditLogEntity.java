package com.flowmind.platform.persistence.entity;
import lombok.Data;
import java.time.LocalDateTime;
/** 审计日志表 process_audit_log 的持久化实体。
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 **/
@Data
public class ProcessAuditLogEntity {
    /**
     * 审计日志主键。
     */
    private String id;
    /**
     * 关联流程实例 ID，定义期操作可为空。
     */
    private String instanceId;
    /**
     * 关联操作幂等号。
     */
    private String operationId;
    /**
     * 审计目标类型，典型值：DEFINITION、INSTANCE、TASK、ATTACHMENT。
     */
    private String targetType;
    /**
     * 审计目标 ID。
     */
    private String targetId;
    /**
     * 动作类型。运行时动作直接使用动作名，流程定义管理动作使用 DEFINITION_* 命名空间。
     */
    private String actionType;
    /**
     * 操作人 ID。
     */
    private String operatorId;
    /**
     * 审计详情 JSON 对象，包含变更前后值和操作上下文。
     */
    private String detailJson;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
}

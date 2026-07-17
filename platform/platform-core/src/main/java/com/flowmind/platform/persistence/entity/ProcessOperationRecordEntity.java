package com.flowmind.platform.persistence.entity;
import lombok.Data;
import java.time.LocalDateTime;
/** 操作幂等记录表 process_operation_record 的持久化实体。
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 **/
@Data
public class ProcessOperationRecordEntity {
    /**
     * 操作记录主键。
     */
    private String id;
    /**
     * 客户端提供的全局唯一操作幂等号。
     */
    private String operationId;
    /**
     * 关联流程实例 ID。
     */
    private String instanceId;
    /**
     * 关联活动任务 ID。
     */
    private String taskId;
    /**
     * 动作类型。运行时动作直接使用动作名，流程定义管理动作使用 DEFINITION_* 命名空间。
     */
    private String actionType;
    /**
     * 操作人 ID。
     */
    private String operatorId;
    /**
     * 规范化请求内容的摘要哈希。
     */
    private String requestHash;
    /**
     * 操作状态，典型值：PROCESSING、SUCCESS、FAILED。
     */
    private String operationStatus;
    /**
     * 成功结果 JSON 对象，用于幂等重试返回原结果。
     */
    private String resultJson;
    /**
     * 确定性失败错误码。
     */
    private String errorCode;
    /**
     * PROCESSING 状态的处理租约到期时间。
     */
    private LocalDateTime processingExpiresAt;
    /**
     * 幂等记录保留到期时间。
     */
    private LocalDateTime expiresAt;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;
}

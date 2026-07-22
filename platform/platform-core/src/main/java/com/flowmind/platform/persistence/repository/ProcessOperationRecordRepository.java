package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.persistence.entity.ProcessOperationRecordEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作幂等记录仓储，只负责 process_operation_record 持久化访问。
 *
 * @author Yuxin Xu
 * @since 2026-07-17
 */
@Repository
public class ProcessOperationRecordRepository {

    private static final RowMapper<ProcessOperationRecordEntity> ROW_MAPPER =
            new RowMapper<ProcessOperationRecordEntity>() {
                @Override
                public ProcessOperationRecordEntity mapRow(ResultSet resultSet, int rowNum) throws SQLException {
                    ProcessOperationRecordEntity entity = new ProcessOperationRecordEntity();
                    entity.setId(resultSet.getString("id"));
                    entity.setOperationId(resultSet.getString("operation_id"));
                    entity.setInstanceId(resultSet.getString("instance_id"));
                    entity.setTaskId(resultSet.getString("task_id"));
                    entity.setActionType(resultSet.getString("action_type"));
                    entity.setOperatorId(resultSet.getString("operator_id"));
                    entity.setRequestHash(resultSet.getString("request_hash"));
                    entity.setOperationStatus(resultSet.getString("operation_status"));
                    entity.setResultJson(resultSet.getString("result_json"));
                    entity.setErrorCode(resultSet.getString("error_code"));
                    entity.setProcessingExpiresAt(
                            DefinitionRowMappers.toLocalDateTime(resultSet.getString("processing_expires_at")));
                    entity.setExpiresAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("expires_at")));
                    entity.setCreatedAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("created_at")));
                    entity.setUpdatedAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("updated_at")));
                    return entity;
                }
            };

    private final JdbcTemplate jdbcTemplate;

    public ProcessOperationRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ProcessOperationRecordEntity findByOperationId(String operationId) {
        List<ProcessOperationRecordEntity> results = jdbcTemplate.query(
                "SELECT * FROM process_operation_record WHERE operation_id = ?",
                ROW_MAPPER, operationId);
        return results.isEmpty() ? null : results.get(0);
    }

    public int insert(ProcessOperationRecordEntity entity) {
        return jdbcTemplate.update("INSERT INTO process_operation_record "
                        + "(id, operation_id, instance_id, task_id, action_type, operator_id, request_hash, "
                        + "operation_status, result_json, error_code, processing_expires_at, expires_at, "
                        + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE(?, 'PROCESSING'), "
                        + "?, ?, ?, ?, COALESCE(?, datetime('now')), COALESCE(?, datetime('now')))",
                entity.getId(),
                entity.getOperationId(),
                entity.getInstanceId(),
                entity.getTaskId(),
                entity.getActionType(),
                entity.getOperatorId(),
                entity.getRequestHash(),
                entity.getOperationStatus(),
                entity.getResultJson(),
                entity.getErrorCode(),
                DefinitionRowMappers.toDbString(entity.getProcessingExpiresAt()),
                DefinitionRowMappers.toDbString(entity.getExpiresAt()),
                DefinitionRowMappers.toDbString(entity.getCreatedAt()),
                DefinitionRowMappers.toDbString(entity.getUpdatedAt()));
    }

    /**
     * 将幂等记录更新为 SUCCESS。
     *
     * @param operationId 客户端幂等操作号，定位唯一操作记录
     * @param resultJson  成功结果 JSON，后续重放时直接从该字段恢复返回值
     * @return 受影响行数
     */
    public int markSuccess(String operationId, String resultJson) {
        return jdbcTemplate.update("UPDATE process_operation_record "
                        + "SET operation_status = 'SUCCESS', result_json = ?, error_code = NULL, "
                        + "updated_at = datetime('now') WHERE operation_id = ?",
                resultJson, operationId);
    }

    /**
     * 将幂等记录更新为 FAILED。
     *
     * @param operationId 客户端幂等操作号，定位唯一操作记录
     * @param errorCode   确定性失败错误码，供调用方识别失败原因
     * @return 受影响行数
     */
    public int markFailed(String operationId, String errorCode) {
        return jdbcTemplate.update("UPDATE process_operation_record "
                        + "SET operation_status = 'FAILED', result_json = NULL, error_code = ?, "
                        + "updated_at = datetime('now') WHERE operation_id = ?",
                errorCode, operationId);
    }

    /**
     * 延长 PROCESSING 记录租约，用于接管已过期但未进入终态的操作。
     *
     * @param operationId          客户端幂等操作号，定位唯一操作记录
     * @param processingExpiresAt  新的 PROCESSING 租约截止时间
     * @return 受影响行数
     */
    public int extendProcessingLease(String operationId, java.time.LocalDateTime processingExpiresAt) {
        return jdbcTemplate.update("UPDATE process_operation_record "
                        + "SET processing_expires_at = ?, updated_at = datetime('now') "
                        + "WHERE operation_id = ? AND operation_status = 'PROCESSING'",
                DefinitionRowMappers.toDbString(processingExpiresAt), operationId);
    }

    /**
     * 原子接管已过期的 PROCESSING 租约。
     *
     * <p>过期判断被放入 UPDATE 条件中，多个恢复请求同时到达时至多一个请求会更新成功。</p>
     *
     * @param operationId         客户端幂等操作号
     * @param now                 本次接管时观察的业务时间
     * @param processingExpiresAt 接管后的新租约截止时间
     * @return 成功取得接管权时返回 {@code 1}，否则返回 {@code 0}
     */
    public int takeOverExpiredProcessingLease(String operationId,
                                              java.time.LocalDateTime now,
                                              java.time.LocalDateTime processingExpiresAt) {
        return jdbcTemplate.update("UPDATE process_operation_record "
                        + "SET processing_expires_at = ?, updated_at = ? "
                        + "WHERE operation_id = ? AND operation_status = 'PROCESSING' "
                        + "AND processing_expires_at <= ?",
                DefinitionRowMappers.toDbString(processingExpiresAt),
                DefinitionRowMappers.toDbString(now),
                operationId,
                DefinitionRowMappers.toDbString(now));
    }

    /**
     * 为幂等记录补充实例和任务目标，并拒绝与已绑定目标不一致的覆盖。
     *
     * @param operationId 幂等操作号
     * @param instanceId  流程实例 ID，可为空
     * @param taskId      活动任务 ID，可为空
     * @return 成功绑定或原目标一致时返回受影响行数
     */
    public int bindTargetIfCompatible(String operationId, String instanceId, String taskId) {
        return jdbcTemplate.update("UPDATE process_operation_record "
                        + "SET instance_id = COALESCE(instance_id, ?), task_id = COALESCE(task_id, ?), "
                        + "updated_at = datetime('now') WHERE operation_id = ? "
                        + "AND (? IS NULL OR instance_id IS NULL OR instance_id = ?) "
                        + "AND (? IS NULL OR task_id IS NULL OR task_id = ?)",
                instanceId, taskId, operationId,
                instanceId, instanceId,
                taskId, taskId);
    }

    /**
     * Extends an expired PROCESSING lease with CAS semantics. The update succeeds only when the lease observed by the
     * caller is still the current database value.
     */
    public int extendProcessingLeaseIfExpired(String operationId,
                                              LocalDateTime observedProcessingExpiresAt,
                                              LocalDateTime now,
                                              LocalDateTime newProcessingExpiresAt) {
        return jdbcTemplate.update("UPDATE process_operation_record "
                        + "SET processing_expires_at = ?, updated_at = datetime('now') "
                        + "WHERE operation_id = ? AND operation_status = 'PROCESSING' "
                        + "AND processing_expires_at = ? AND processing_expires_at <= ?",
                DefinitionRowMappers.toDbString(newProcessingExpiresAt),
                operationId,
                DefinitionRowMappers.toDbString(observedProcessingExpiresAt),
                DefinitionRowMappers.toDbString(now));
    }

    /**
     * 查询已超过保留窗口的幂等记录，供后续清理任务使用。
     *
     * @param now   当前时间
     * @param limit 最大返回条数；小于等于 0 时返回空列表
     * @return 按过期时间稳定排序的幂等记录
     */
    public List<ProcessOperationRecordEntity> findExpired(LocalDateTime now, int limit) {
        if (limit <= 0) {
            return java.util.Collections.emptyList();
        }
        return jdbcTemplate.query("SELECT * FROM process_operation_record "
                        + "WHERE expires_at <= ? ORDER BY expires_at ASC, created_at ASC, id ASC LIMIT ?",
                ROW_MAPPER, DefinitionRowMappers.toDbString(now), Integer.valueOf(limit));
    }
}

package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.persistence.entity.ProcessOperationRecordEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
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
}

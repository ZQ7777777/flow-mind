package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.api.dto.CallbackLogQuery;
import com.flowmind.platform.persistence.entity.ProcessCallbackLogEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 回调日志仓储，统一访问 process_callback_log。
 */
@Repository
public class ProcessCallbackLogRepository {

    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private static final RowMapper<ProcessCallbackLogEntity> ROW_MAPPER =
            new RowMapper<ProcessCallbackLogEntity>() {
                @Override
                public ProcessCallbackLogEntity mapRow(ResultSet resultSet, int rowNum) throws SQLException {
                    ProcessCallbackLogEntity entity = new ProcessCallbackLogEntity();
                    entity.setId(resultSet.getString("id"));
                    entity.setEventId(resultSet.getString("event_id"));
                    entity.setInstanceId(resultSet.getString("instance_id"));
                    entity.setOperationId(resultSet.getString("operation_id"));
                    entity.setEventType(resultSet.getString("event_type"));
                    entity.setActionType(resultSet.getString("action_type"));
                    entity.setPayloadJson(resultSet.getString("payload_json"));
                    entity.setCallbackStatus(resultSet.getString("callback_status"));
                    entity.setRetryCount(Integer.valueOf(resultSet.getInt("retry_count")));
                    entity.setLastError(resultSet.getString("last_error"));
                    entity.setCreatedAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("created_at")));
                    entity.setUpdatedAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("updated_at")));
                    return entity;
                }
            };

    private final JdbcTemplate jdbcTemplate;

    public ProcessCallbackLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ProcessCallbackLogEntity findByEventId(String eventId) {
        List<ProcessCallbackLogEntity> results = jdbcTemplate.query(
                "SELECT * FROM process_callback_log WHERE event_id = ?",
                ROW_MAPPER, eventId);
        return results.isEmpty() ? null : results.get(0);
    }

    public int insertPending(ProcessCallbackLogEntity entity) {
        return jdbcTemplate.update("INSERT INTO process_callback_log "
                        + "(id, event_id, instance_id, operation_id, event_type, action_type, payload_json, "
                        + "callback_status, retry_count, last_error, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', COALESCE(?, 0), ?, "
                        + "COALESCE(?, datetime('now')), COALESCE(?, datetime('now')))",
                entity.getId(),
                entity.getEventId(),
                entity.getInstanceId(),
                entity.getOperationId(),
                entity.getEventType(),
                entity.getActionType(),
                entity.getPayloadJson(),
                entity.getRetryCount(),
                entity.getLastError(),
                DefinitionRowMappers.toDbString(entity.getCreatedAt()),
                DefinitionRowMappers.toDbString(entity.getUpdatedAt()));
    }

    public int markSuccess(String eventId) {
        return jdbcTemplate.update("UPDATE process_callback_log "
                        + "SET callback_status = 'SUCCESS', last_error = NULL, updated_at = datetime('now') "
                        + "WHERE event_id = ?",
                eventId);
    }

    public int markFailed(String eventId, String lastError) {
        return jdbcTemplate.update("UPDATE process_callback_log "
                        + "SET callback_status = 'FAILED', retry_count = retry_count + 1, "
                        + "last_error = ?, updated_at = datetime('now') WHERE event_id = ?",
                lastError, eventId);
    }

    public List<ProcessCallbackLogEntity> query(CallbackLogQuery query) {
        CallbackLogQuery normalized = query == null ? new CallbackLogQuery() : query;
        int pageNo = normalizePageNo(normalized.getPageNo());
        int pageSize = normalizePageSize(normalized.getPageSize());
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("SELECT * FROM process_callback_log ");
        appendWhere(sql, params, normalized);
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?");
        params.add(Integer.valueOf(pageSize));
        params.add(Integer.valueOf((pageNo - 1) * pageSize));
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public long count(CallbackLogQuery query) {
        CallbackLogQuery normalized = query == null ? new CallbackLogQuery() : query;
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(1) FROM process_callback_log ");
        appendWhere(sql, params, normalized);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count == null ? 0L : count.longValue();
    }

    public List<ProcessCallbackLogEntity> findPending(int limit) {
        if (limit <= 0) {
            return java.util.Collections.emptyList();
        }
        return jdbcTemplate.query("SELECT * FROM process_callback_log WHERE callback_status = 'PENDING' "
                        + "ORDER BY created_at ASC, id ASC LIMIT ?",
                ROW_MAPPER, Integer.valueOf(limit));
    }

    private void appendWhere(StringBuilder sql, List<Object> params, CallbackLogQuery query) {
        sql.append("WHERE 1 = 1 ");
        if (query.getInstanceId() != null && !query.getInstanceId().trim().isEmpty()) {
            sql.append("AND instance_id = ? ");
            params.add(query.getInstanceId());
        }
        if (query.getEventType() != null) {
            sql.append("AND event_type = ? ");
            params.add(query.getEventType().name());
        }
        if (query.getCallbackStatus() != null) {
            sql.append("AND callback_status = ? ");
            params.add(query.getCallbackStatus().name());
        }
    }

    private static int normalizePageNo(Integer pageNo) {
        if (pageNo == null || pageNo.intValue() < 1) {
            return DEFAULT_PAGE_NO;
        }
        return pageNo.intValue();
    }

    private static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize.intValue() < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize.intValue(), MAX_PAGE_SIZE);
    }
}

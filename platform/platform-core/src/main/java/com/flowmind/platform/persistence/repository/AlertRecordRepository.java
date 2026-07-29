package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.api.dto.AlertQuery;
import com.flowmind.platform.persistence.entity.ProcessAlertRecordEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Repository for alert records. */
@Repository
public class AlertRecordRepository {

    private static final RowMapper<ProcessAlertRecordEntity> ROW_MAPPER =
            new RowMapper<ProcessAlertRecordEntity>() {
                @Override
                public ProcessAlertRecordEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
                    ProcessAlertRecordEntity entity = new ProcessAlertRecordEntity();
                    entity.setId(rs.getString("id"));
                    entity.setInstanceId(rs.getString("instance_id"));
                    entity.setTaskId(rs.getString("task_id"));
                    entity.setAlertType(rs.getString("alert_type"));
                    entity.setSeverity(rs.getString("severity"));
                    entity.setAlertStatus(rs.getString("alert_status"));
                    entity.setDetailJson(rs.getString("detail_json"));
                    entity.setHandledBy(rs.getString("handled_by"));
                    entity.setHandledAt(DefinitionRowMappers.toLocalDateTime(rs.getString("handled_at")));
                    entity.setCreatedAt(DefinitionRowMappers.toLocalDateTime(rs.getString("created_at")));
                    return entity;
                }
            };

    private final JdbcTemplate jdbcTemplate;

    public AlertRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insert(ProcessAlertRecordEntity entity) {
        return jdbcTemplate.update("INSERT INTO process_alert_record "
                        + "(id, instance_id, task_id, alert_type, severity, alert_status, detail_json, "
                        + "handled_by, handled_at, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, COALESCE(?, 'OPEN'), ?, ?, ?, COALESCE(?, datetime('now')))",
                entity.getId(), entity.getInstanceId(), entity.getTaskId(), entity.getAlertType(),
                entity.getSeverity(), entity.getAlertStatus(), entity.getDetailJson(), entity.getHandledBy(),
                DefinitionRowMappers.toDbString(entity.getHandledAt()),
                DefinitionRowMappers.toDbString(entity.getCreatedAt()));
    }

    public ProcessAlertRecordEntity findById(String id) {
        List<ProcessAlertRecordEntity> results = jdbcTemplate.query(
                "SELECT * FROM process_alert_record WHERE id = ?", ROW_MAPPER, id);
        return results.isEmpty() ? null : results.get(0);
    }

    public ProcessAlertRecordEntity findOpenByTaskAndType(String taskId, String alertType) {
        List<ProcessAlertRecordEntity> results = jdbcTemplate.query(
                "SELECT * FROM process_alert_record WHERE task_id = ? AND alert_type = ? AND alert_status = 'OPEN' "
                        + "ORDER BY created_at DESC, id DESC LIMIT 1",
                ROW_MAPPER, taskId, alertType);
        return results.isEmpty() ? null : results.get(0);
    }

    /** Find an open alert by JSON detail field value for idempotent alert creation. */
    public ProcessAlertRecordEntity findOpenByTypeAndDetailValue(String alertType, String detailField, String value) {
        if (isBlank(detailField)) {
            throw new IllegalArgumentException("detailField must not be blank");
        }
        List<ProcessAlertRecordEntity> results = jdbcTemplate.query(
                "SELECT * FROM process_alert_record WHERE alert_type = ? AND alert_status = 'OPEN' "
                        + "AND json_extract(detail_json, ?) = ? ORDER BY created_at DESC, id DESC LIMIT 1",
                ROW_MAPPER, alertType, "$." + detailField, value);
        return results.isEmpty() ? null : results.get(0);
    }

    public int handle(String id, String targetStatus, String handledBy, LocalDateTime handledAt) {
        return jdbcTemplate.update("UPDATE process_alert_record SET alert_status = ?, handled_by = ?, handled_at = ? "
                        + "WHERE id = ? AND alert_status = 'OPEN'",
                targetStatus, handledBy, DefinitionRowMappers.toDbString(handledAt), id);
    }

    public List<ProcessAlertRecordEntity> query(AlertQuery query) {
        int pageNo = com.flowmind.platform.core.query.PageQueryNormalizer.normalizePageNo(query.getPageNo());
        int pageSize = com.flowmind.platform.core.query.PageQueryNormalizer.normalizePageSize(query.getPageSize());
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("SELECT * FROM process_alert_record WHERE 1 = 1 ");
        appendFilters(sql, params, query);
        sql.append("ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?");
        params.add(Integer.valueOf(pageSize));
        params.add(Integer.valueOf((pageNo - 1) * pageSize));
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public long count(AlertQuery query) {
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(1) FROM process_alert_record WHERE 1 = 1 ");
        appendFilters(sql, params, query);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count == null ? 0L : count.longValue();
    }

    private void appendFilters(StringBuilder sql, List<Object> params, AlertQuery query) {
        if (!isBlank(query.getInstanceId())) {
            sql.append("AND instance_id = ? ");
            params.add(query.getInstanceId());
        }
        if (!isBlank(query.getTaskId())) {
            sql.append("AND task_id = ? ");
            params.add(query.getTaskId());
        }
        if (query.getAlertType() != null) {
            sql.append("AND alert_type = ? ");
            params.add(query.getAlertType().name());
        }
        if (query.getSeverity() != null) {
            sql.append("AND severity = ? ");
            params.add(query.getSeverity().name());
        }
        if (query.getAlertStatus() != null) {
            sql.append("AND alert_status = ? ");
            params.add(query.getAlertStatus().name());
        }
        if (query.getCreatedFrom() != null) {
            sql.append("AND created_at >= ? ");
            params.add(DefinitionRowMappers.toDbString(query.getCreatedFrom()));
        }
        if (query.getCreatedTo() != null) {
            sql.append("AND created_at <= ? ");
            params.add(DefinitionRowMappers.toDbString(query.getCreatedTo()));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

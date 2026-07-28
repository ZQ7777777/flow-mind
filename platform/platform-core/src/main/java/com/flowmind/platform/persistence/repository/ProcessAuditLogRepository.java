package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.api.dto.AuditLogQuery;
import com.flowmind.platform.persistence.entity.ProcessAuditLogEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Repository for {@code process_audit_log}. */
@Repository
public class ProcessAuditLogRepository {

    private static final RowMapper<ProcessAuditLogEntity> ROW_MAPPER =
            new RowMapper<ProcessAuditLogEntity>() {
                @Override
                public ProcessAuditLogEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
                    ProcessAuditLogEntity entity = new ProcessAuditLogEntity();
                    entity.setId(rs.getString("id"));
                    entity.setInstanceId(rs.getString("instance_id"));
                    entity.setOperationId(rs.getString("operation_id"));
                    entity.setTargetType(rs.getString("target_type"));
                    entity.setTargetId(rs.getString("target_id"));
                    entity.setActionType(rs.getString("action_type"));
                    entity.setOperatorId(rs.getString("operator_id"));
                    entity.setDetailJson(rs.getString("detail_json"));
                    entity.setCreatedAt(DefinitionRowMappers.toLocalDateTime(rs.getString("created_at")));
                    return entity;
                }
            };

    private final JdbcTemplate jdbcTemplate;

    public ProcessAuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insert(ProcessAuditLogEntity entity) {
        return jdbcTemplate.update("INSERT INTO process_audit_log "
                        + "(id, instance_id, operation_id, target_type, target_id, action_type, operator_id, "
                        + "detail_json, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, datetime('now')))",
                entity.getId(), entity.getInstanceId(), entity.getOperationId(), entity.getTargetType(),
                entity.getTargetId(), entity.getActionType(), entity.getOperatorId(), entity.getDetailJson(),
                DefinitionRowMappers.toDbString(entity.getCreatedAt()));
    }

    public List<ProcessAuditLogEntity> query(AuditLogQuery query) {
        int pageNo = com.flowmind.platform.core.query.PageQueryNormalizer.normalizePageNo(query.getPageNo());
        int pageSize = com.flowmind.platform.core.query.PageQueryNormalizer.normalizePageSize(query.getPageSize());
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("SELECT * FROM process_audit_log WHERE 1 = 1 ");
        appendFilters(sql, params, query);
        sql.append("ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?");
        params.add(Integer.valueOf(pageSize));
        params.add(Integer.valueOf((pageNo - 1) * pageSize));
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public long count(AuditLogQuery query) {
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(1) FROM process_audit_log WHERE 1 = 1 ");
        appendFilters(sql, params, query);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count == null ? 0L : count.longValue();
    }

    private void appendFilters(StringBuilder sql, List<Object> params, AuditLogQuery query) {
        if (!isBlank(query.getInstanceId())) {
            sql.append("AND instance_id = ? ");
            params.add(query.getInstanceId());
        }
        if (query.getTargetType() != null) {
            sql.append("AND target_type = ? ");
            params.add(query.getTargetType().name());
        }
        if (!isBlank(query.getTargetId())) {
            sql.append("AND target_id = ? ");
            params.add(query.getTargetId());
        }
        if (!isBlank(query.getOperatorUserId())) {
            sql.append("AND operator_id = ? ");
            params.add(query.getOperatorUserId());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

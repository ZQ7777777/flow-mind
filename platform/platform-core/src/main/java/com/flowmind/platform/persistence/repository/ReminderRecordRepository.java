package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.api.dto.ReminderQuery;
import com.flowmind.platform.persistence.entity.ProcessReminderRecordEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Repository for reminder records. */
@Repository
public class ReminderRecordRepository {

    private static final RowMapper<ProcessReminderRecordEntity> ROW_MAPPER =
            new RowMapper<ProcessReminderRecordEntity>() {
                @Override
                public ProcessReminderRecordEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
                    ProcessReminderRecordEntity entity = new ProcessReminderRecordEntity();
                    entity.setId(rs.getString("id"));
                    entity.setInstanceId(rs.getString("instance_id"));
                    entity.setTaskId(rs.getString("task_id"));
                    entity.setReminderType(rs.getString("reminder_type"));
                    entity.setTargetUserIds(rs.getString("target_user_ids"));
                    entity.setMessage(rs.getString("message"));
                    entity.setReminderStatus(rs.getString("reminder_status"));
                    entity.setErrorMessage(rs.getString("error_message"));
                    entity.setCreatedBy(rs.getString("created_by"));
                    entity.setCreatedAt(DefinitionRowMappers.toLocalDateTime(rs.getString("created_at")));
                    entity.setSentAt(DefinitionRowMappers.toLocalDateTime(rs.getString("sent_at")));
                    return entity;
                }
            };

    private final JdbcTemplate jdbcTemplate;

    public ReminderRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insert(ProcessReminderRecordEntity entity) {
        return jdbcTemplate.update("INSERT INTO process_reminder_record "
                        + "(id, instance_id, task_id, reminder_type, target_user_ids, message, reminder_status, "
                        + "error_message, created_by, created_at, sent_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, COALESCE(?, 'PENDING'), ?, ?, COALESCE(?, datetime('now')), ?)",
                entity.getId(), entity.getInstanceId(), entity.getTaskId(), entity.getReminderType(),
                entity.getTargetUserIds(), entity.getMessage(), entity.getReminderStatus(),
                entity.getErrorMessage(), entity.getCreatedBy(),
                DefinitionRowMappers.toDbString(entity.getCreatedAt()),
                DefinitionRowMappers.toDbString(entity.getSentAt()));
    }

    public ProcessReminderRecordEntity findById(String id) {
        List<ProcessReminderRecordEntity> results = jdbcTemplate.query(
                "SELECT * FROM process_reminder_record WHERE id = ?", ROW_MAPPER, id);
        return results.isEmpty() ? null : results.get(0);
    }

    public int markSent(String id) {
        return jdbcTemplate.update("UPDATE process_reminder_record SET reminder_status = 'SENT', "
                + "sent_at = datetime('now'), error_message = NULL WHERE id = ?", id);
    }

    public int markFailed(String id, String errorMessage) {
        return jdbcTemplate.update("UPDATE process_reminder_record SET reminder_status = 'FAILED', "
                + "error_message = ? WHERE id = ?", errorMessage, id);
    }

    /** Count reminders for a task and trigger type. */
    public long countByTaskAndType(String taskId, String reminderType) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM process_reminder_record "
                        + "WHERE task_id = ? AND reminder_type = ?",
                Long.class, taskId, reminderType);
        return count == null ? 0L : count.longValue();
    }

    public List<ProcessReminderRecordEntity> query(ReminderQuery query) {
        int pageNo = com.flowmind.platform.core.query.PageQueryNormalizer.normalizePageNo(query.getPageNo());
        int pageSize = com.flowmind.platform.core.query.PageQueryNormalizer.normalizePageSize(query.getPageSize());
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("SELECT * FROM process_reminder_record WHERE 1 = 1 ");
        appendFilters(sql, params, query);
        sql.append("ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?");
        params.add(Integer.valueOf(pageSize));
        params.add(Integer.valueOf((pageNo - 1) * pageSize));
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public long count(ReminderQuery query) {
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(1) FROM process_reminder_record WHERE 1 = 1 ");
        appendFilters(sql, params, query);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count == null ? 0L : count.longValue();
    }

    private void appendFilters(StringBuilder sql, List<Object> params, ReminderQuery query) {
        if (!isBlank(query.getInstanceId())) {
            sql.append("AND instance_id = ? ");
            params.add(query.getInstanceId());
        }
        if (!isBlank(query.getTaskId())) {
            sql.append("AND task_id = ? ");
            params.add(query.getTaskId());
        }
        if (query.getReminderType() != null) {
            sql.append("AND reminder_type = ? ");
            params.add(query.getReminderType().name());
        }
        if (query.getReminderStatus() != null) {
            sql.append("AND reminder_status = ? ");
            params.add(query.getReminderStatus().name());
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

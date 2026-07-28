package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.api.dto.ReadRecordQuery;
import com.flowmind.platform.persistence.entity.ProcessReadRecordEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Repository for process read records. */
@Repository
public class ProcessReadRecordRepository {

    private static final RowMapper<ProcessReadRecordEntity> ROW_MAPPER =
            new RowMapper<ProcessReadRecordEntity>() {
                @Override
                public ProcessReadRecordEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
                    ProcessReadRecordEntity entity = new ProcessReadRecordEntity();
                    entity.setId(rs.getString("id"));
                    entity.setInstanceId(rs.getString("instance_id"));
                    entity.setUserId(rs.getString("user_id"));
                    entity.setUserName(rs.getString("user_name"));
                    entity.setReadAt(DefinitionRowMappers.toLocalDateTime(rs.getString("read_at")));
                    return entity;
                }
            };

    private final JdbcTemplate jdbcTemplate;

    public ProcessReadRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ProcessReadRecordEntity upsert(String instanceId, String userId, String userName, LocalDateTime readAt) {
        ProcessReadRecordEntity existing = findByInstanceAndUser(instanceId, userId);
        if (existing == null) {
            String id = UUID.randomUUID().toString();
            jdbcTemplate.update("INSERT INTO process_read_record "
                            + "(id, instance_id, user_id, user_name, read_at) VALUES (?, ?, ?, ?, ?)",
                    id, instanceId, userId, userName, DefinitionRowMappers.toDbString(readAt));
            return findById(id);
        }
        jdbcTemplate.update("UPDATE process_read_record SET user_name = ?, read_at = ? "
                        + "WHERE instance_id = ? AND user_id = ?",
                userName, DefinitionRowMappers.toDbString(readAt), instanceId, userId);
        return findByInstanceAndUser(instanceId, userId);
    }

    public ProcessReadRecordEntity findById(String id) {
        List<ProcessReadRecordEntity> results = jdbcTemplate.query(
                "SELECT * FROM process_read_record WHERE id = ?", ROW_MAPPER, id);
        return results.isEmpty() ? null : results.get(0);
    }

    public ProcessReadRecordEntity findByInstanceAndUser(String instanceId, String userId) {
        List<ProcessReadRecordEntity> results = jdbcTemplate.query(
                "SELECT * FROM process_read_record WHERE instance_id = ? AND user_id = ?",
                ROW_MAPPER, instanceId, userId);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<ProcessReadRecordEntity> query(ReadRecordQuery query) {
        int pageNo = com.flowmind.platform.core.query.PageQueryNormalizer.normalizePageNo(query.getPageNo());
        int pageSize = com.flowmind.platform.core.query.PageQueryNormalizer.normalizePageSize(query.getPageSize());
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("SELECT * FROM process_read_record WHERE 1 = 1 ");
        appendFilters(sql, params, query);
        sql.append("ORDER BY read_at DESC, id DESC LIMIT ? OFFSET ?");
        params.add(Integer.valueOf(pageSize));
        params.add(Integer.valueOf((pageNo - 1) * pageSize));
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public long count(ReadRecordQuery query) {
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(1) FROM process_read_record WHERE 1 = 1 ");
        appendFilters(sql, params, query);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count == null ? 0L : count.longValue();
    }

    private void appendFilters(StringBuilder sql, List<Object> params, ReadRecordQuery query) {
        if (!isBlank(query.getInstanceId())) {
            sql.append("AND instance_id = ? ");
            params.add(query.getInstanceId());
        }
        if (!isBlank(query.getUserId())) {
            sql.append("AND user_id = ? ");
            params.add(query.getUserId());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

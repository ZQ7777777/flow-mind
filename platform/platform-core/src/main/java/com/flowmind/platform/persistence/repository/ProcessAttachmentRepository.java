package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.api.dto.AttachmentQuery;
import com.flowmind.platform.persistence.entity.ProcessAttachmentEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 运行期附件元数据仓储。 */
@Repository
public class ProcessAttachmentRepository {
    private final JdbcTemplate jdbcTemplate;

    public ProcessAttachmentRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public ProcessAttachmentEntity findById(String id) {
        List<ProcessAttachmentEntity> rows = jdbcTemplate.query("SELECT * FROM process_attachment WHERE id = ?", mapper(), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int insert(ProcessAttachmentEntity value) {
        return jdbcTemplate.update("INSERT INTO process_attachment (id, instance_id, task_id, owner_type, attachment_code, field_code, file_name, content_type, size_bytes, storage_key, uploaded_by, uploaded_at, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                value.getId(), value.getInstanceId(), value.getTaskId(), value.getOwnerType(), value.getAttachmentCode(),
                value.getFieldCode(), value.getFileName(), value.getContentType(), value.getSizeBytes(), value.getStorageKey(),
                value.getUploadedBy(), DefinitionRowMappers.toDbString(value.getUploadedAt()));
    }

    /**
     * 在一条语句内确认来源任务仍处于可操作状态，并确认附件数量尚未到达上限后写入元数据。
     */
    public int insertWhenTaskOpenAndWithinLimit(ProcessAttachmentEntity value, Long expectedTaskVersion, Integer maxCount) {
        return jdbcTemplate.update("INSERT INTO process_attachment (id, instance_id, task_id, owner_type, attachment_code, field_code, file_name, content_type, size_bytes, storage_key, uploaded_by, uploaded_at, deleted) "
                        + "SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0 "
                        + "WHERE EXISTS (SELECT 1 FROM process_active_task WHERE id = ? AND instance_id = ? "
                        + "AND task_status IN ('ACTIVE', 'CLAIMED') AND (? IS NULL OR lock_version = ?)) "
                        + "AND (SELECT COUNT(1) FROM process_attachment WHERE instance_id = ? AND attachment_code = ? AND deleted = 0) < ?",
                value.getId(), value.getInstanceId(), value.getTaskId(), value.getOwnerType(), value.getAttachmentCode(),
                value.getFieldCode(), value.getFileName(), value.getContentType(), value.getSizeBytes(), value.getStorageKey(),
                value.getUploadedBy(), DefinitionRowMappers.toDbString(value.getUploadedAt()),
                value.getTaskId(), value.getInstanceId(), expectedTaskVersion, expectedTaskVersion,
                value.getInstanceId(), value.getAttachmentCode(), maxCount);
    }

    public List<ProcessAttachmentEntity> queryActive(AttachmentQuery query) {
        StringBuilder sql = new StringBuilder("SELECT * FROM process_attachment WHERE instance_id = ? AND deleted = 0");
        List<Object> args = new ArrayList<Object>(); args.add(query.getInstanceId());
        if (hasText(query.getTaskId())) { sql.append(" AND task_id = ?"); args.add(query.getTaskId()); }
        if (query.getOwnerType() != null) { sql.append(" AND owner_type = ?"); args.add(query.getOwnerType().name()); }
        if (hasText(query.getAttachmentCode())) { sql.append(" AND attachment_code = ?"); args.add(query.getAttachmentCode()); }
        if (hasText(query.getFieldCode())) { sql.append(" AND field_code = ?"); args.add(query.getFieldCode()); }
        sql.append(" ORDER BY uploaded_at ASC, id ASC");
        return jdbcTemplate.query(sql.toString(), mapper(), args.toArray());
    }

    public long countActiveByInstanceAndCode(String instanceId, String attachmentCode) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM process_attachment WHERE instance_id = ? AND attachment_code = ? AND deleted = 0", Long.class, instanceId, attachmentCode);
        return count == null ? 0L : count.longValue();
    }

    public int softDelete(String id, String deletedBy, LocalDateTime deletedAt) {
        return jdbcTemplate.update("UPDATE process_attachment SET deleted = 1, deleted_by = ?, deleted_at = ? WHERE id = ? AND deleted = 0",
                deletedBy, DefinitionRowMappers.toDbString(deletedAt), id);
    }

    /** 仅当附件来源任务仍未完成时，原子地执行软删除。 */
    public int softDeleteWhenTaskOpen(String id, String taskId, String instanceId, String deletedBy, LocalDateTime deletedAt) {
        return jdbcTemplate.update("UPDATE process_attachment SET deleted = 1, deleted_by = ?, deleted_at = ? "
                        + "WHERE id = ? AND deleted = 0 AND EXISTS (SELECT 1 FROM process_active_task "
                        + "WHERE id = ? AND instance_id = ? AND task_status IN ('ACTIVE', 'CLAIMED'))",
                deletedBy, DefinitionRowMappers.toDbString(deletedAt), id, taskId, instanceId);
    }

    public List<String> findStorageKeysByInstanceId(String instanceId) {
        return jdbcTemplate.queryForList("SELECT storage_key FROM process_attachment WHERE instance_id = ?", String.class, instanceId);
    }

    private RowMapper<ProcessAttachmentEntity> mapper() {
        return new RowMapper<ProcessAttachmentEntity>() {
            @Override public ProcessAttachmentEntity mapRow(ResultSet rs, int row) throws SQLException {
                ProcessAttachmentEntity e = new ProcessAttachmentEntity();
                e.setId(rs.getString("id")); e.setInstanceId(rs.getString("instance_id")); e.setTaskId(rs.getString("task_id"));
                e.setOwnerType(rs.getString("owner_type")); e.setAttachmentCode(rs.getString("attachment_code")); e.setFieldCode(rs.getString("field_code"));
                e.setFileName(rs.getString("file_name")); e.setContentType(rs.getString("content_type")); e.setSizeBytes(Long.valueOf(rs.getLong("size_bytes")));
                e.setStorageKey(rs.getString("storage_key")); e.setUploadedBy(rs.getString("uploaded_by"));
                e.setUploadedAt(DefinitionRowMappers.toLocalDateTime(rs.getString("uploaded_at"))); e.setDeleted(Boolean.valueOf(rs.getBoolean("deleted")));
                e.setDeletedBy(rs.getString("deleted_by")); e.setDeletedAt(DefinitionRowMappers.toLocalDateTime(rs.getString("deleted_at"))); return e;
            }
        };
    }
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
}

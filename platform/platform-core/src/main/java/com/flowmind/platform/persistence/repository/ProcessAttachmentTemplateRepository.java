package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.persistence.entity.ProcessAttachmentTemplateEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for global attachment template versions.
 */
@Repository
public class ProcessAttachmentTemplateRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProcessAttachmentTemplateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insert(ProcessAttachmentTemplateEntity entity) {
        return jdbcTemplate.update("INSERT INTO process_attachment_template "
                        + "(id, attachment_code, template_version, attachment_name, description, "
                        + "allowed_extensions, max_size_bytes, template_status, created_by, updated_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                entity.getId(), entity.getAttachmentCode(), entity.getTemplateVersion(),
                entity.getAttachmentName(), entity.getDescription(), entity.getAllowedExtensions(),
                entity.getMaxSizeBytes(), entity.getTemplateStatus(), entity.getCreatedBy(), entity.getUpdatedBy());
    }

    public int findMaxVersionByAttachmentCode(String attachmentCode) {
        Integer maxVersion = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(template_version), 0) FROM process_attachment_template "
                        + "WHERE attachment_code = ?",
                Integer.class, attachmentCode);
        return maxVersion == null ? 0 : maxVersion;
    }

    public Optional<ProcessAttachmentTemplateEntity> findById(String id) {
        List<ProcessAttachmentTemplateEntity> templates = jdbcTemplate.query(
                "SELECT id, attachment_code, template_version, attachment_name, description, "
                        + "allowed_extensions, max_size_bytes, template_status, created_by, created_at, "
                        + "updated_by, updated_at FROM process_attachment_template WHERE id = ?",
                (PreparedStatement preparedStatement) -> preparedStatement.setString(1, id),
                rowMapper());
        return templates.isEmpty() ? Optional.empty() : Optional.of(templates.get(0));
    }

    public List<ProcessAttachmentTemplateEntity> findByAttachmentCode(String attachmentCode) {
        return jdbcTemplate.query("SELECT id, attachment_code, template_version, attachment_name, description, "
                        + "allowed_extensions, max_size_bytes, template_status, created_by, created_at, "
                        + "updated_by, updated_at FROM process_attachment_template "
                        + "WHERE attachment_code = ? ORDER BY template_version DESC",
                (PreparedStatement preparedStatement) -> preparedStatement.setString(1, attachmentCode),
                rowMapper());
    }

    public List<ProcessAttachmentTemplateEntity> findTemplates(String attachmentCode,
                                                               String templateStatus,
                                                               Integer templateVersion) {
        StringBuilder sql = new StringBuilder("SELECT id, attachment_code, template_version, attachment_name, "
                + "description, allowed_extensions, max_size_bytes, template_status, created_by, created_at, "
                + "updated_by, updated_at FROM process_attachment_template WHERE 1 = 1");
        java.util.List<Object> args = new java.util.ArrayList<Object>();
        if (attachmentCode != null && !attachmentCode.trim().isEmpty()) {
            sql.append(" AND attachment_code = ?");
            args.add(attachmentCode);
        }
        if (templateStatus != null && !templateStatus.trim().isEmpty()) {
            sql.append(" AND template_status = ?");
            args.add(templateStatus);
        }
        if (templateVersion != null) {
            sql.append(" AND template_version = ?");
            args.add(templateVersion);
        }
        sql.append(" ORDER BY attachment_code ASC, template_version DESC");
        return jdbcTemplate.query(sql.toString(), (PreparedStatement preparedStatement) -> {
            for (int index = 0; index < args.size(); index++) {
                preparedStatement.setObject(index + 1, args.get(index));
            }
        }, rowMapper());
    }

    public int updateStatus(String id, String templateStatus, String updatedBy) {
        return jdbcTemplate.update("UPDATE process_attachment_template "
                        + "SET template_status = ?, updated_by = ?, updated_at = datetime('now') "
                        + "WHERE id = ?",
                templateStatus, updatedBy, id);
    }

    public int updateTemplate(String id,
                              String attachmentName,
                              String description,
                              String allowedExtensions,
                              Long maxSizeBytes,
                              String templateStatus,
                              String updatedBy) {
        return jdbcTemplate.update("UPDATE process_attachment_template "
                        + "SET attachment_name = ?, description = ?, allowed_extensions = ?, "
                        + "max_size_bytes = ?, template_status = ?, updated_by = ?, updated_at = datetime('now') "
                        + "WHERE id = ?",
                attachmentName, description, allowedExtensions, maxSizeBytes, templateStatus, updatedBy, id);
    }

    public boolean isReferencedByActiveConfig(String templateId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM process_definition_attachment_config "
                        + "WHERE attachment_template_id = ? AND config_status = 'ACTIVE'",
                Integer.class, templateId);
        return count != null && count > 0;
    }

    public boolean isReferencedByEffectiveConfig(String templateId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM process_definition_attachment_config "
                        + "WHERE attachment_template_id = ? AND config_status IN ('ACTIVE', 'INACTIVE')",
                Integer.class, templateId);
        return count != null && count > 0;
    }

    private RowMapper<ProcessAttachmentTemplateEntity> rowMapper() {
        return (resultSet, rowNum) -> {
            ProcessAttachmentTemplateEntity entity = new ProcessAttachmentTemplateEntity();
            entity.setId(resultSet.getString("id"));
            entity.setAttachmentCode(resultSet.getString("attachment_code"));
            entity.setTemplateVersion(getNullableInteger(resultSet.getInt("template_version"), resultSet.wasNull()));
            entity.setAttachmentName(resultSet.getString("attachment_name"));
            entity.setDescription(resultSet.getString("description"));
            entity.setAllowedExtensions(resultSet.getString("allowed_extensions"));
            entity.setMaxSizeBytes(getNullableLong(resultSet.getLong("max_size_bytes"), resultSet.wasNull()));
            entity.setTemplateStatus(resultSet.getString("template_status"));
            entity.setCreatedBy(resultSet.getString("created_by"));
            entity.setCreatedAt(getDateTime(resultSet, "created_at"));
            entity.setUpdatedBy(resultSet.getString("updated_by"));
            entity.setUpdatedAt(getDateTime(resultSet, "updated_at"));
            return entity;
        };
    }

    private Integer getNullableInteger(int value, boolean wasNull) {
        return wasNull ? null : value;
    }

    private Long getNullableLong(long value, boolean wasNull) {
        return wasNull ? null : value;
    }

    private LocalDateTime getDateTime(ResultSet resultSet, String column) throws SQLException {
        String value = resultSet.getString(column);
        if (value == null || value.isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(value.replace(' ', 'T'));
    }
}

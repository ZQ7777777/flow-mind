package com.flowmind.business.entry;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 业务入口配置 SQLite 仓储。
 *
 * @author FlowMind
 * @since 2026-08-18
 */
@Repository
public class BusinessEntryConfigRepository {

    private static final DateTimeFormatter DB_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final RowMapper<BusinessEntryConfigEntity> ROW_MAPPER =
            new RowMapper<BusinessEntryConfigEntity>() {
                @Override
                public BusinessEntryConfigEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
                    BusinessEntryConfigEntity entity = new BusinessEntryConfigEntity();
                    entity.setId(rs.getString("id"));
                    entity.setDefinitionId(rs.getString("definition_id"));
                    entity.setEntryDisplayName(rs.getString("entry_display_name"));
                    entity.setEntryPageUrl(rs.getString("entry_page_url"));
                    entity.setEntrySource(BusinessEntrySource.valueOf(rs.getString("entry_source")));
                    entity.setEnabled(Boolean.valueOf(rs.getInt("enabled") == 1));
                    entity.setRemark(rs.getString("remark"));
                    entity.setGenerationId(rs.getString("generation_id"));
                    entity.setArtifactRevision(rs.getString("artifact_revision"));
                    entity.setCreatedBy(rs.getString("created_by"));
                    entity.setCreatedAt(toLocalDateTime(rs.getString("created_at")));
                    entity.setUpdatedBy(rs.getString("updated_by"));
                    entity.setUpdatedAt(toLocalDateTime(rs.getString("updated_at")));
                    return entity;
                }
            };

    private final JdbcTemplate jdbcTemplate;

    public BusinessEntryConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BusinessEntryConfigEntity> findAll() {
        return jdbcTemplate.query("SELECT * FROM business_entry_config ORDER BY updated_at DESC, id ASC",
                ROW_MAPPER);
    }

    public Optional<BusinessEntryConfigEntity> findById(String id) {
        return first(jdbcTemplate.query("SELECT * FROM business_entry_config WHERE id = ?", ROW_MAPPER, id));
    }

    public Optional<BusinessEntryConfigEntity> findByDefinitionId(String definitionId) {
        return first(jdbcTemplate.query("SELECT * FROM business_entry_config WHERE definition_id = ?",
                ROW_MAPPER, definitionId));
    }

    public int insert(BusinessEntryConfigEntity entity) {
        return jdbcTemplate.update("INSERT INTO business_entry_config "
                        + "(id, definition_id, entry_display_name, entry_page_url, entry_source, enabled, remark, "
                        + "generation_id, artifact_revision, created_by, updated_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                entity.getId(), entity.getDefinitionId(), entity.getEntryDisplayName(), entity.getEntryPageUrl(),
                entity.getEntrySource().name(), Boolean.TRUE.equals(entity.getEnabled()) ? 1 : 0,
                entity.getRemark(), entity.getGenerationId(), entity.getArtifactRevision(), entity.getCreatedBy(),
                entity.getUpdatedBy());
    }

    public int updateById(BusinessEntryConfigEntity entity) {
        return jdbcTemplate.update("UPDATE business_entry_config SET entry_display_name = ?, entry_page_url = ?, "
                        + "entry_source = ?, enabled = ?, remark = ?, generation_id = ?, artifact_revision = ?, "
                        + "updated_by = ?, updated_at = datetime('now') WHERE id = ?",
                entity.getEntryDisplayName(), entity.getEntryPageUrl(), entity.getEntrySource().name(),
                Boolean.TRUE.equals(entity.getEnabled()) ? 1 : 0, entity.getRemark(), entity.getGenerationId(),
                entity.getArtifactRevision(), entity.getUpdatedBy(), entity.getId());
    }

    public int upsertByDefinitionId(BusinessEntryConfigEntity entity) {
        return jdbcTemplate.update("INSERT INTO business_entry_config "
                        + "(id, definition_id, entry_display_name, entry_page_url, entry_source, enabled, remark, "
                        + "generation_id, artifact_revision, created_by, updated_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(definition_id) DO UPDATE SET entry_display_name = excluded.entry_display_name, "
                        + "entry_page_url = excluded.entry_page_url, entry_source = excluded.entry_source, "
                        + "enabled = excluded.enabled, remark = excluded.remark, "
                        + "generation_id = excluded.generation_id, artifact_revision = excluded.artifact_revision, "
                        + "updated_by = excluded.updated_by, updated_at = datetime('now')",
                entity.getId(), entity.getDefinitionId(), entity.getEntryDisplayName(), entity.getEntryPageUrl(),
                entity.getEntrySource().name(), Boolean.TRUE.equals(entity.getEnabled()) ? 1 : 0,
                entity.getRemark(), entity.getGenerationId(), entity.getArtifactRevision(), entity.getCreatedBy(),
                entity.getUpdatedBy());
    }

    public int deleteById(String id) {
        return jdbcTemplate.update("DELETE FROM business_entry_config WHERE id = ?", id);
    }

    private Optional<BusinessEntryConfigEntity> first(List<BusinessEntryConfigEntity> rows) {
        return rows.isEmpty() ? Optional.<BusinessEntryConfigEntity>empty() : Optional.of(rows.get(0));
    }

    private static LocalDateTime toLocalDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim().replace('T', ' ');
        if (normalized.length() > 19) {
            normalized = normalized.substring(0, 19);
        }
        return LocalDateTime.parse(normalized, DB_TIME);
    }
}

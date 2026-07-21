package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.persistence.entity.ProcessDefinitionAttachmentConfigEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Repository for process definition attachment configurations.
 */
@Repository
public class ProcessDefinitionAttachmentConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProcessDefinitionAttachmentConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void replaceDraftGroup(String definitionId, String attachmentConfigId,
                                  List<ProcessDefinitionAttachmentConfigEntity> configs) {
        jdbcTemplate.update("DELETE FROM process_definition_attachment_config "
                        + "WHERE definition_id = ? AND attachment_config_id = ? AND config_status = 'DRAFT'",
                definitionId, attachmentConfigId);
        if (configs == null || configs.isEmpty()) {
            return;
        }
        for (ProcessDefinitionAttachmentConfigEntity config : configs) {
            ProcessDefinitionAttachmentConfigEntity entity = copyForDefinitionAndGroup(
                    config, definitionId, attachmentConfigId, config.getId());
            entity.setConfigStatus("DRAFT");
            insert(entity);
        }
    }

    public int insert(ProcessDefinitionAttachmentConfigEntity entity) {
        return jdbcTemplate.update("INSERT INTO process_definition_attachment_config "
                        + "(id, attachment_config_id, definition_id, config_status, activated_at, "
                        + "attachment_template_id, attachment_code, required, min_count, max_count, "
                        + "applicable_node_codes, sort_order, created_by, updated_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                entity.getId(), entity.getAttachmentConfigId(), entity.getDefinitionId(),
                entity.getConfigStatus(), toSqlDateTime(entity.getActivatedAt()), entity.getAttachmentTemplateId(),
                entity.getAttachmentCode(), toSqlBoolean(entity.getRequired()), entity.getMinCount(),
                entity.getMaxCount(), entity.getApplicableNodeCodes(), entity.getSortOrder(),
                entity.getCreatedBy(), entity.getUpdatedBy());
    }

    public List<ProcessDefinitionAttachmentConfigEntity> findByDefinitionId(String definitionId) {
        return jdbcTemplate.query(selectSql()
                        + " WHERE definition_id = ? ORDER BY attachment_config_id ASC, sort_order ASC, attachment_code ASC",
                (PreparedStatement preparedStatement) -> preparedStatement.setString(1, definitionId),
                rowMapper());
    }

    public List<ProcessDefinitionAttachmentConfigEntity> findByDefinitionIdAndStatus(String definitionId,
                                                                                     String configStatus) {
        return jdbcTemplate.query(selectSql()
                        + " WHERE definition_id = ? AND config_status = ? "
                        + "ORDER BY attachment_config_id ASC, sort_order ASC, attachment_code ASC",
                (PreparedStatement preparedStatement) -> {
                    preparedStatement.setString(1, definitionId);
                    preparedStatement.setString(2, configStatus);
                }, rowMapper());
    }

    public List<ProcessDefinitionAttachmentConfigEntity> findActiveByDefinitionId(String definitionId) {
        return findByDefinitionIdAndStatus(definitionId, "ACTIVE");
    }

    public List<ProcessDefinitionAttachmentConfigEntity> findByAttachmentConfigId(String attachmentConfigId) {
        return jdbcTemplate.query(selectSql()
                        + " WHERE attachment_config_id = ? ORDER BY sort_order ASC, attachment_code ASC",
                (PreparedStatement preparedStatement) -> preparedStatement.setString(1, attachmentConfigId),
                rowMapper());
    }

    public List<ProcessDefinitionAttachmentConfigEntity> findByDefinitionIdAndAttachmentConfigId(
            String definitionId, String attachmentConfigId) {
        return jdbcTemplate.query(selectSql()
                        + " WHERE definition_id = ? AND attachment_config_id = ? "
                        + "ORDER BY sort_order ASC, attachment_code ASC",
                (PreparedStatement preparedStatement) -> {
                    preparedStatement.setString(1, definitionId);
                    preparedStatement.setString(2, attachmentConfigId);
                },
                rowMapper());
    }

    public int copyToDefinition(String sourceDefinitionId, String targetDefinitionId) {
        List<ProcessDefinitionAttachmentConfigEntity> sourceConfigs = findByDefinitionId(sourceDefinitionId);
        Map<String, String> groupIdMapping = new LinkedHashMap<String, String>();
        int copied = 0;
        for (ProcessDefinitionAttachmentConfigEntity sourceConfig : sourceConfigs) {
            String targetGroupId = groupIdMapping.computeIfAbsent(sourceConfig.getAttachmentConfigId(),
                    ignored -> newId());
            ProcessDefinitionAttachmentConfigEntity targetConfig =
                    copyForDefinitionAndGroup(sourceConfig, targetDefinitionId, targetGroupId, newId());
            targetConfig.setConfigStatus("DRAFT");
            targetConfig.setActivatedAt(null);
            copied += insert(targetConfig);
        }
        return copied;
    }

    public int deleteDraftByDefinitionId(String definitionId) {
        return jdbcTemplate.update("DELETE FROM process_definition_attachment_config "
                + "WHERE definition_id = ? AND config_status = 'DRAFT'", definitionId);
    }

    public int deleteByDefinitionId(String definitionId) {
        return jdbcTemplate.update("DELETE FROM process_definition_attachment_config WHERE definition_id = ?",
                definitionId);
    }

    @Transactional
    public int activateGroup(String definitionId, String attachmentConfigId, String updatedBy) {
        jdbcTemplate.update("UPDATE process_definition_attachment_config "
                        + "SET config_status = 'INACTIVE', updated_by = ?, updated_at = datetime('now') "
                        + "WHERE definition_id = ? AND config_status = 'ACTIVE' "
                        + "AND attachment_config_id <> ?",
                updatedBy, definitionId, attachmentConfigId);
        return jdbcTemplate.update("UPDATE process_definition_attachment_config "
                        + "SET config_status = 'ACTIVE', activated_at = datetime('now'), "
                        + "updated_by = ?, updated_at = datetime('now') "
                        + "WHERE definition_id = ? AND attachment_config_id = ?",
                updatedBy, definitionId, attachmentConfigId);
    }

    private String selectSql() {
        return "SELECT id, attachment_config_id, definition_id, config_status, activated_at, "
                + "attachment_template_id, attachment_code, required, min_count, max_count, "
                + "applicable_node_codes, sort_order, created_by, created_at, updated_by, updated_at "
                + "FROM process_definition_attachment_config";
    }

    private ProcessDefinitionAttachmentConfigEntity copyForDefinitionAndGroup(
            ProcessDefinitionAttachmentConfigEntity source, String definitionId, String attachmentConfigId, String id) {
        ProcessDefinitionAttachmentConfigEntity target = new ProcessDefinitionAttachmentConfigEntity();
        target.setId(id == null || id.isEmpty() ? newId() : id);
        target.setAttachmentConfigId(attachmentConfigId);
        target.setDefinitionId(definitionId);
        target.setConfigStatus(source.getConfigStatus());
        target.setActivatedAt(source.getActivatedAt());
        target.setAttachmentTemplateId(source.getAttachmentTemplateId());
        target.setAttachmentCode(source.getAttachmentCode());
        target.setRequired(source.getRequired());
        target.setMinCount(source.getMinCount());
        target.setMaxCount(source.getMaxCount());
        target.setApplicableNodeCodes(source.getApplicableNodeCodes());
        target.setSortOrder(source.getSortOrder());
        target.setCreatedBy(source.getCreatedBy());
        target.setUpdatedBy(source.getUpdatedBy());
        return target;
    }

    private RowMapper<ProcessDefinitionAttachmentConfigEntity> rowMapper() {
        return (resultSet, rowNum) -> {
            ProcessDefinitionAttachmentConfigEntity entity = new ProcessDefinitionAttachmentConfigEntity();
            entity.setId(resultSet.getString("id"));
            entity.setAttachmentConfigId(resultSet.getString("attachment_config_id"));
            entity.setDefinitionId(resultSet.getString("definition_id"));
            entity.setConfigStatus(resultSet.getString("config_status"));
            entity.setActivatedAt(getDateTime(resultSet, "activated_at"));
            entity.setAttachmentTemplateId(resultSet.getString("attachment_template_id"));
            entity.setAttachmentCode(resultSet.getString("attachment_code"));
            entity.setRequired(resultSet.getBoolean("required"));
            entity.setMinCount(getNullableInteger(resultSet.getInt("min_count"), resultSet.wasNull()));
            entity.setMaxCount(getNullableInteger(resultSet.getInt("max_count"), resultSet.wasNull()));
            entity.setApplicableNodeCodes(resultSet.getString("applicable_node_codes"));
            entity.setSortOrder(getNullableInteger(resultSet.getInt("sort_order"), resultSet.wasNull()));
            entity.setCreatedBy(resultSet.getString("created_by"));
            entity.setCreatedAt(getDateTime(resultSet, "created_at"));
            entity.setUpdatedBy(resultSet.getString("updated_by"));
            entity.setUpdatedAt(getDateTime(resultSet, "updated_at"));
            return entity;
        };
    }

    private Integer toSqlBoolean(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    private String toSqlDateTime(LocalDateTime value) {
        return value == null ? null : value.toString().replace('T', ' ');
    }

    private Integer getNullableInteger(int value, boolean wasNull) {
        return wasNull ? null : value;
    }

    private LocalDateTime getDateTime(ResultSet resultSet, String column) throws SQLException {
        String value = resultSet.getString(column);
        if (value == null || value.isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(value.replace(' ', 'T'));
    }

    private String newId() {
        return UUID.randomUUID().toString();
    }
}

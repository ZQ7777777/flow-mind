package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.persistence.entity.ProcessDefinitionAttachmentConfigEntity;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 流程定义附件配置仓储，只负责 process_definition_attachment_config 持久化访问。
 *
 * @author Yuxin Xu
 * @since 2026-07-17
 */
@Repository
public class ProcessDefinitionAttachmentConfigRepository {

    //把附件表中的一行数据转换成一个java对象
    private static final RowMapper<ProcessDefinitionAttachmentConfigEntity> ROW_MAPPER =
            new RowMapper<ProcessDefinitionAttachmentConfigEntity>() {
                @Override
                public ProcessDefinitionAttachmentConfigEntity mapRow(ResultSet resultSet, int rowNum)
                        throws SQLException {
                    ProcessDefinitionAttachmentConfigEntity entity = new ProcessDefinitionAttachmentConfigEntity();
                    entity.setId(resultSet.getString("id"));
                    entity.setAttachmentConfigId(resultSet.getString("attachment_config_id"));
                    entity.setDefinitionId(resultSet.getString("definition_id"));
                    entity.setConfigStatus(resultSet.getString("config_status"));
                    entity.setActivatedAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("activated_at")));
                    entity.setAttachmentTemplateId(resultSet.getString("attachment_template_id"));
                    entity.setAttachmentCode(resultSet.getString("attachment_code"));
                    entity.setRequired(Boolean.valueOf(resultSet.getInt("required") == 1));
                    entity.setMinCount(Integer.valueOf(resultSet.getInt("min_count")));
                    int maxCount = resultSet.getInt("max_count");
                    entity.setMaxCount(resultSet.wasNull() ? null : Integer.valueOf(maxCount));
                    entity.setApplicableNodeCodes(resultSet.getString("applicable_node_codes"));
                    entity.setSortOrder(Integer.valueOf(resultSet.getInt("sort_order")));
                    entity.setCreatedBy(resultSet.getString("created_by"));
                    entity.setCreatedAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("created_at")));
                    entity.setUpdatedBy(resultSet.getString("updated_by"));
                    entity.setUpdatedAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("updated_at")));
                    return entity;
                }
            };

    private final JdbcTemplate jdbcTemplate;

    public ProcessDefinitionAttachmentConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 根据流程定义id查询附件配置
     * @param definitionId
     * @return
     */
    public List<ProcessDefinitionAttachmentConfigEntity> findByDefinitionId(String definitionId) {
        return jdbcTemplate.query("SELECT * FROM process_definition_attachment_config WHERE definition_id = ? "
                        + "ORDER BY sort_order ASC, attachment_code ASC",
                ROW_MAPPER, definitionId);
    }

    /**
     * 批量保存附件配置
     * @param attachmentConfigs
     * @return
     */
    public int[] batchInsert(final List<ProcessDefinitionAttachmentConfigEntity> attachmentConfigs) {
        return jdbcTemplate.batchUpdate("INSERT INTO process_definition_attachment_config "
                        + "(id, attachment_config_id, definition_id, config_status, activated_at, "
                        + "attachment_template_id, attachment_code, required, min_count, max_count, "
                        + "applicable_node_codes, sort_order, created_by, created_at, updated_by, updated_at) "
                        + "VALUES (?, ?, ?, COALESCE(?, 'DRAFT'), ?, ?, ?, COALESCE(?, 0), "
                        + "COALESCE(?, 0), ?, ?, COALESCE(?, 0), ?, COALESCE(?, datetime('now')), "
                        + "?, COALESCE(?, datetime('now')))",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ProcessDefinitionAttachmentConfigEntity attachmentConfig = attachmentConfigs.get(i);
                        ps.setString(1, attachmentConfig.getId());
                        ps.setString(2, attachmentConfig.getAttachmentConfigId());
                        ps.setString(3, attachmentConfig.getDefinitionId());
                        ps.setString(4, attachmentConfig.getConfigStatus());
                        ps.setString(5, DefinitionRowMappers.toDbString(attachmentConfig.getActivatedAt()));
                        ps.setString(6, attachmentConfig.getAttachmentTemplateId());
                        ps.setString(7, attachmentConfig.getAttachmentCode());
                        JdbcBindingUtils.setNullableBoolean(ps, 8, attachmentConfig.getRequired());
                        JdbcBindingUtils.setNullableInteger(ps, 9, attachmentConfig.getMinCount());
                        JdbcBindingUtils.setNullableInteger(ps, 10, attachmentConfig.getMaxCount());
                        ps.setString(11, attachmentConfig.getApplicableNodeCodes());
                        JdbcBindingUtils.setNullableInteger(ps, 12, attachmentConfig.getSortOrder());
                        ps.setString(13, attachmentConfig.getCreatedBy());
                        ps.setString(14, DefinitionRowMappers.toDbString(attachmentConfig.getCreatedAt()));
                        ps.setString(15, attachmentConfig.getUpdatedBy());
                        ps.setString(16, DefinitionRowMappers.toDbString(attachmentConfig.getUpdatedAt()));
                    }

                    @Override
                    public int getBatchSize() {
                        return attachmentConfigs == null ? 0 : attachmentConfigs.size();
                    }
                });
    }

    /**
     * 根据流程定义id删除所有附件配置
     * @param definitionId
     * @return
     */
    public int deleteByDefinitionId(String definitionId) {
        return jdbcTemplate.update("DELETE FROM process_definition_attachment_config WHERE definition_id = ?",
                definitionId);
    }
}

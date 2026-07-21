package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.persistence.entity.ProcessFormFieldEntity;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 流程表单字段仓储，只负责 process_form_field 持久化访问。
 *
 * @author Yuxin Xu
 * @since 2026-07-17
 */
@Repository
public class ProcessFormFieldRepository {

    private static final RowMapper<ProcessFormFieldEntity> ROW_MAPPER = new RowMapper<ProcessFormFieldEntity>() {
        @Override
        public ProcessFormFieldEntity mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            ProcessFormFieldEntity entity = new ProcessFormFieldEntity();
            entity.setId(resultSet.getString("id"));
            entity.setDefinitionId(resultSet.getString("definition_id"));
            entity.setFieldCode(resultSet.getString("field_code"));
            entity.setFieldName(resultSet.getString("field_name"));
            entity.setFieldType(resultSet.getString("field_type"));
            entity.setControlType(resultSet.getString("control_type"));
            entity.setRequired(Boolean.valueOf(resultSet.getInt("required") == 1));
            entity.setValidationRule(resultSet.getString("validation_rule"));
            entity.setDefaultValue(resultSet.getString("default_value"));
            entity.setSortOrder(Integer.valueOf(resultSet.getInt("sort_order")));
            return entity;
        }
    };

    private final JdbcTemplate jdbcTemplate;

    public ProcessFormFieldRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ProcessFormFieldEntity> findByDefinitionId(String definitionId) {
        return jdbcTemplate.query("SELECT * FROM process_form_field WHERE definition_id = ? "
                        + "ORDER BY sort_order ASC, field_code ASC",
                ROW_MAPPER, definitionId);
    }

    public int[] batchInsert(final List<ProcessFormFieldEntity> formFields) {
        return jdbcTemplate.batchUpdate("INSERT INTO process_form_field "
                        + "(id, definition_id, field_code, field_name, field_type, control_type, required, "
                        + "validation_rule, default_value, sort_order) "
                        + "VALUES (?, ?, ?, ?, ?, ?, COALESCE(?, 0), ?, ?, COALESCE(?, 0))",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ProcessFormFieldEntity formField = formFields.get(i);
                        ps.setString(1, formField.getId());
                        ps.setString(2, formField.getDefinitionId());
                        ps.setString(3, formField.getFieldCode());
                        ps.setString(4, formField.getFieldName());
                        ps.setString(5, formField.getFieldType());
                        ps.setString(6, formField.getControlType());
                        JdbcBindingUtils.setNullableBoolean(ps, 7, formField.getRequired());
                        ps.setString(8, formField.getValidationRule());
                        ps.setString(9, formField.getDefaultValue());
                        JdbcBindingUtils.setNullableInteger(ps, 10, formField.getSortOrder());
                    }

                    @Override
                    public int getBatchSize() {
                        return formFields == null ? 0 : formFields.size();
                    }
                });
    }

    public int deleteByDefinitionId(String definitionId) {
        return jdbcTemplate.update("DELETE FROM process_form_field WHERE definition_id = ?", definitionId);
    }
}

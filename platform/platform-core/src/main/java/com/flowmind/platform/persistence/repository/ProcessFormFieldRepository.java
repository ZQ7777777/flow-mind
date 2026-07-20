package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.persistence.entity.ProcessFormFieldEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

/**
 * 流程定义表单字段仓储。
 *
 * <p>本仓储只负责 process_form_field 的 SQLite 访问与实体映射，不保存用户填写的表单值。</p>
 *
 * @author FlowMind
 * @since 2026-07-17
 */
@Repository
public class ProcessFormFieldRepository {

    private final JdbcTemplate jdbcTemplate;

    /** 创建表单字段仓储。 */
    public ProcessFormFieldRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 按定义替换保存字段列表。 */
    public void replaceByDefinitionId(String definitionId, List<ProcessFormFieldEntity> fields) {
        deleteByDefinitionId(definitionId);
        if (fields == null || fields.isEmpty()) {
            return;
        }
        for (ProcessFormFieldEntity field : fields) {
            ProcessFormFieldEntity entity = copyForDefinition(field, definitionId, field.getId());
            insert(entity);
        }
    }

    /** 新增单个表单字段。 */
    public int insert(ProcessFormFieldEntity entity) {
        return jdbcTemplate.update("INSERT INTO process_form_field "
                        + "(id, definition_id, field_code, field_name, field_type, control_type, "
                        + "required, validation_rule, default_value, sort_order) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                entity.getId(), entity.getDefinitionId(), entity.getFieldCode(), entity.getFieldName(),
                entity.getFieldType(), entity.getControlType(), toSqlBoolean(entity.getRequired()),
                entity.getValidationRule(), entity.getDefaultValue(), entity.getSortOrder());
    }

    /** 按定义查询字段，按 sort_order、field_code 稳定排序。 */
    public List<ProcessFormFieldEntity> findByDefinitionId(String definitionId) {
        return jdbcTemplate.query("SELECT id, definition_id, field_code, field_name, field_type, "
                        + "control_type, required, validation_rule, default_value, sort_order "
                        + "FROM process_form_field WHERE definition_id = ? "
                        + "ORDER BY sort_order ASC, field_code ASC",
                (PreparedStatement preparedStatement) -> preparedStatement.setString(1, definitionId),
                rowMapper());
    }

    /** 复制源定义字段到目标定义，复制时生成新的字段主键。 */
    public int copyToDefinition(String sourceDefinitionId, String targetDefinitionId) {
        List<ProcessFormFieldEntity> sourceFields = findByDefinitionId(sourceDefinitionId);
        int copied = 0;
        for (ProcessFormFieldEntity sourceField : sourceFields) {
            copied += insert(copyForDefinition(sourceField, targetDefinitionId, newId()));
        }
        return copied;
    }

    /** 删除指定定义下的全部表单字段。 */
    public int deleteByDefinitionId(String definitionId) {
        return jdbcTemplate.update("DELETE FROM process_form_field WHERE definition_id = ?", definitionId);
    }

    private ProcessFormFieldEntity copyForDefinition(ProcessFormFieldEntity source, String definitionId, String id) {
        ProcessFormFieldEntity target = new ProcessFormFieldEntity();
        target.setId(id == null || id.isEmpty() ? newId() : id);
        target.setDefinitionId(definitionId);
        target.setFieldCode(source.getFieldCode());
        target.setFieldName(source.getFieldName());
        target.setFieldType(source.getFieldType());
        target.setControlType(source.getControlType());
        target.setRequired(source.getRequired());
        target.setValidationRule(source.getValidationRule());
        target.setDefaultValue(source.getDefaultValue());
        target.setSortOrder(source.getSortOrder());
        return target;
    }

    private RowMapper<ProcessFormFieldEntity> rowMapper() {
        return (resultSet, rowNum) -> {
            ProcessFormFieldEntity entity = new ProcessFormFieldEntity();
            entity.setId(resultSet.getString("id"));
            entity.setDefinitionId(resultSet.getString("definition_id"));
            entity.setFieldCode(resultSet.getString("field_code"));
            entity.setFieldName(resultSet.getString("field_name"));
            entity.setFieldType(resultSet.getString("field_type"));
            entity.setControlType(resultSet.getString("control_type"));
            entity.setRequired(resultSet.getBoolean("required"));
            entity.setValidationRule(resultSet.getString("validation_rule"));
            entity.setDefaultValue(resultSet.getString("default_value"));
            entity.setSortOrder(getNullableInteger(resultSet.getInt("sort_order"), resultSet.wasNull()));
            return entity;
        };
    }

    private Integer toSqlBoolean(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    private Integer getNullableInteger(int value, boolean wasNull) {
        return wasNull ? null : value;
    }

    private String newId() {
        return UUID.randomUUID().toString();
    }
}

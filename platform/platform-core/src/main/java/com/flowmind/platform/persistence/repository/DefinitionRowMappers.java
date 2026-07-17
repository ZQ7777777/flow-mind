package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.persistence.entity.ProcessDefinitionEntity;
import com.flowmind.platform.persistence.entity.ProcessEdgeEntity;
import com.flowmind.platform.persistence.entity.ProcessNodeEntity;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class DefinitionRowMappers {

    static final RowMapper<ProcessDefinitionEntity> DEFINITION = new RowMapper<ProcessDefinitionEntity>() {
        @Override
        public ProcessDefinitionEntity mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            ProcessDefinitionEntity entity = new ProcessDefinitionEntity();
            entity.setId(resultSet.getString("id"));
            entity.setProcessCode(resultSet.getString("process_code"));
            entity.setProcessName(resultSet.getString("process_name"));
            entity.setSystemCode(resultSet.getString("system_code"));
            entity.setVersion(Integer.valueOf(resultSet.getInt("version")));
            entity.setDefinitionStatus(resultSet.getString("definition_status"));
            entity.setActivationStatus(resultSet.getString("activation_status"));
            entity.setGrayStatus(resultSet.getString("gray_status"));
            entity.setGrayRuleConfig(resultSet.getString("gray_rule_config"));
            entity.setArchivedBy(resultSet.getString("archived_by"));
            entity.setArchivedAt(toLocalDateTime(resultSet.getString("archived_at")));
            entity.setRemark(resultSet.getString("remark"));
            entity.setCreatedBy(resultSet.getString("created_by"));
            entity.setCreatedAt(toLocalDateTime(resultSet.getString("created_at")));
            entity.setUpdatedBy(resultSet.getString("updated_by"));
            entity.setUpdatedAt(toLocalDateTime(resultSet.getString("updated_at")));
            return entity;
        }
    };

    static final RowMapper<ProcessNodeEntity> NODE = new RowMapper<ProcessNodeEntity>() {
        @Override
        public ProcessNodeEntity mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            ProcessNodeEntity entity = new ProcessNodeEntity();
            entity.setId(resultSet.getString("id"));
            entity.setDefinitionId(resultSet.getString("definition_id"));
            entity.setNodeCode(resultSet.getString("node_code"));
            entity.setNodeName(resultSet.getString("node_name"));
            entity.setNodeType(resultSet.getString("node_type"));
            entity.setPairedGatewayCode(resultSet.getString("paired_gateway_code"));
            entity.setApproverRuleType(resultSet.getString("approver_rule_type"));
            entity.setApproverRuleConfig(resultSet.getString("approver_rule_config"));
            entity.setMultiInstanceMode(resultSet.getString("multi_instance_mode"));
            entity.setListenerConfig(resultSet.getString("listener_config"));
            entity.setTimeoutConfig(resultSet.getString("timeout_config"));
            entity.setReminderConfig(resultSet.getString("reminder_config"));
            entity.setPositionX(getNullableDouble(resultSet, "position_x"));
            entity.setPositionY(getNullableDouble(resultSet, "position_y"));
            entity.setSortOrder(Integer.valueOf(resultSet.getInt("sort_order")));
            return entity;
        }
    };

    static final RowMapper<ProcessEdgeEntity> EDGE = new RowMapper<ProcessEdgeEntity>() {
        @Override
        public ProcessEdgeEntity mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            ProcessEdgeEntity entity = new ProcessEdgeEntity();
            entity.setId(resultSet.getString("id"));
            entity.setDefinitionId(resultSet.getString("definition_id"));
            entity.setEdgeCode(resultSet.getString("edge_code"));
            entity.setSourceNodeCode(resultSet.getString("source_node_code"));
            entity.setTargetNodeCode(resultSet.getString("target_node_code"));
            entity.setConditionExpression(resultSet.getString("condition_expression"));
            entity.setDefaultEdge(Boolean.valueOf(resultSet.getInt("default_edge") == 1));
            entity.setSortOrder(Integer.valueOf(resultSet.getInt("sort_order")));
            return entity;
        }
    };

    private static final DateTimeFormatter SQLITE_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DefinitionRowMappers() {
    }

    static String toDbString(LocalDateTime value) {
        return value == null ? null : value.format(SQLITE_DATE_TIME);
    }

    private static LocalDateTime toLocalDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() >= 19) {
            normalized = normalized.substring(0, 19);
        }
        if (normalized.indexOf('T') >= 0) {
            return LocalDateTime.parse(normalized);
        }
        return LocalDateTime.parse(normalized, SQLITE_DATE_TIME);
    }

    private static Double getNullableDouble(ResultSet resultSet, String columnName) throws SQLException {
        double value = resultSet.getDouble(columnName);
        return resultSet.wasNull() ? null : Double.valueOf(value);
    }
}

package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.persistence.entity.ProcessNodeEntity;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * 流程节点仓储，只负责 process_node 持久化访问。
 */
@Repository
public class ProcessNodeRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProcessNodeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 根据流程定义id查询所有节点
     * @param definitionId
     * @return
     */
    public List<ProcessNodeEntity> findByDefinitionId(String definitionId) {
        return jdbcTemplate.query("SELECT * FROM process_node WHERE definition_id = ? "
                        + "ORDER BY sort_order ASC, node_code ASC",
                DefinitionRowMappers.NODE, definitionId);
    }

    /** Read one node by definition id and node code. */
    public ProcessNodeEntity findByDefinitionIdAndNodeCode(String definitionId, String nodeCode) {
        List<ProcessNodeEntity> results = jdbcTemplate.query("SELECT * FROM process_node "
                        + "WHERE definition_id = ? AND node_code = ?",
                DefinitionRowMappers.NODE, definitionId, nodeCode);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 一次性插入多个节点
     * @param nodes
     * @return
     */
    public int[] batchInsert(final List<ProcessNodeEntity> nodes) {
        return jdbcTemplate.batchUpdate("INSERT INTO process_node "
                        + "(id, definition_id, node_code, node_name, node_type, paired_gateway_code, "
                        + "approver_rule_type, approver_rule_config, multi_instance_mode, listener_config, "
                        + "timeout_config, reminder_config, position_x, position_y, sort_order) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, 'SINGLE'), ?, ?, ?, ?, ?, COALESCE(?, 0))",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ProcessNodeEntity node = nodes.get(i);
                        ps.setString(1, node.getId());
                        ps.setString(2, node.getDefinitionId());
                        ps.setString(3, node.getNodeCode());
                        ps.setString(4, node.getNodeName());
                        ps.setString(5, node.getNodeType());
                        ps.setString(6, node.getPairedGatewayCode());
                        ps.setString(7, node.getApproverRuleType());
                        ps.setString(8, node.getApproverRuleConfig());
                        ps.setString(9, node.getMultiInstanceMode());
                        ps.setString(10, node.getListenerConfig());
                        ps.setString(11, node.getTimeoutConfig());
                        ps.setString(12, node.getReminderConfig());
                        JdbcBindingUtils.setNullableDouble(ps, 13, node.getPositionX());
                        JdbcBindingUtils.setNullableDouble(ps, 14, node.getPositionY());
                        JdbcBindingUtils.setNullableInteger(ps, 15, node.getSortOrder());
                    }

                    @Override
                    public int getBatchSize() {
                        return nodes == null ? 0 : nodes.size();
                    }
                });
    }

    /**
     * 根据流程定义Id删除所有节点
     * @param definitionId
     * @return
     */
    public int deleteByDefinitionId(String definitionId) {
        return jdbcTemplate.update("DELETE FROM process_node WHERE definition_id = ?", definitionId);
    }
}

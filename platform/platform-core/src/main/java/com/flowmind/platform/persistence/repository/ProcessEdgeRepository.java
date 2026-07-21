package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.persistence.entity.ProcessEdgeEntity;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * 流程连线仓储，只负责 process_edge 持久化访问。
 */
@Repository
public class ProcessEdgeRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProcessEdgeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 根据流程定义id查询所有的连线
     * @param definitionId
     * @return
     */
    public List<ProcessEdgeEntity> findByDefinitionId(String definitionId) {
        return jdbcTemplate.query("SELECT * FROM process_edge WHERE definition_id = ? "
                        + "ORDER BY sort_order ASC, edge_code ASC",
                DefinitionRowMappers.EDGE, definitionId);
    }

    /**
     * 一次性插入所有连线
     * @param edges
     * @return
     */
    public int[] batchInsert(final List<ProcessEdgeEntity> edges) {
        return jdbcTemplate.batchUpdate("INSERT INTO process_edge "
                        + "(id, definition_id, edge_code, source_node_code, target_node_code, "
                        + "condition_expression, default_edge, sort_order) "
                        + "VALUES (?, ?, ?, ?, ?, ?, COALESCE(?, 0), COALESCE(?, 0))",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ProcessEdgeEntity edge = edges.get(i);
                        ps.setString(1, edge.getId());
                        ps.setString(2, edge.getDefinitionId());
                        ps.setString(3, edge.getEdgeCode());
                        ps.setString(4, edge.getSourceNodeCode());
                        ps.setString(5, edge.getTargetNodeCode());
                        ps.setString(6, edge.getConditionExpression());
                        JdbcBindingUtils.setNullableBoolean(ps, 7, edge.getDefaultEdge());
                        JdbcBindingUtils.setNullableInteger(ps, 8, edge.getSortOrder());
                    }

                    /**
                     * 返回一共要插入的连线数量
                     * @return
                     */
                    @Override
                    public int getBatchSize() {
                        return edges == null ? 0 : edges.size();
                    }
                });
    }

    /**
     * 根据流程定义id删除连线
     * @param definitionId
     * @return
     */
    public int deleteByDefinitionId(String definitionId) {
        return jdbcTemplate.update("DELETE FROM process_edge WHERE definition_id = ?", definitionId);
    }
}

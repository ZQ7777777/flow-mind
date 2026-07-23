package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.api.dto.StartedInstanceQuery;
import com.flowmind.platform.core.query.PageQueryNormalizer;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 流程运行实例的持久化仓储，只提供实例生命周期中的基础读写原语。
 *
 * @author FlowMind
 * @since 2026-07-22
 */
@Repository
public class ProcessInstanceRepository {

    private static final RowMapper<ProcessInstanceEntity> ROW_MAPPER =
            new RowMapper<ProcessInstanceEntity>() {
                @Override
                public ProcessInstanceEntity mapRow(ResultSet resultSet, int rowNum) throws SQLException {
                    ProcessInstanceEntity entity = new ProcessInstanceEntity();
                    entity.setId(resultSet.getString("id"));
                    entity.setDefinitionId(resultSet.getString("definition_id"));
                    entity.setAttachmentConfigId(resultSet.getString("attachment_config_id"));
                    entity.setProcessCode(resultSet.getString("process_code"));
                    entity.setProcessName(resultSet.getString("process_name"));
                    entity.setVersion(Integer.valueOf(resultSet.getInt("version")));
                    entity.setInstanceTitle(resultSet.getString("instance_title"));
                    entity.setBusinessKey(resultSet.getString("business_key"));
                    entity.setStarterUserId(resultSet.getString("starter_user_id"));
                    entity.setStarterUserName(resultSet.getString("starter_user_name"));
                    entity.setStarterDeptId(resultSet.getString("starter_dept_id"));
                    entity.setCurrentNodeCodes(resultSet.getString("current_node_codes"));
                    entity.setVariablesJson(resultSet.getString("variables_json"));
                    entity.setInstanceStatus(resultSet.getString("instance_status"));
                    entity.setStartedAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("started_at")));
                    entity.setEndedAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("ended_at")));
                    return entity;
                }
            };

    private final JdbcTemplate jdbcTemplate;

    /** 创建流程实例仓储。 */
    public ProcessInstanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 插入流程实例快照。 */
    public int insert(ProcessInstanceEntity entity) {
        return jdbcTemplate.update("INSERT INTO process_instance "
                        + "(id, definition_id, attachment_config_id, process_code, process_name, version, "
                        + "instance_title, business_key, starter_user_id, starter_user_name, starter_dept_id, "
                        + "current_node_codes, variables_json, instance_status, started_at, ended_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, 'NOT_STARTED'), ?, ?)",
                entity.getId(), entity.getDefinitionId(), entity.getAttachmentConfigId(), entity.getProcessCode(),
                entity.getProcessName(), entity.getVersion(), entity.getInstanceTitle(), entity.getBusinessKey(),
                entity.getStarterUserId(), entity.getStarterUserName(), entity.getStarterDeptId(),
                entity.getCurrentNodeCodes(), entity.getVariablesJson(), entity.getInstanceStatus(),
                DefinitionRowMappers.toDbString(entity.getStartedAt()),
                DefinitionRowMappers.toDbString(entity.getEndedAt()));
    }

    /** 按实例 ID 读取实例快照；不存在时返回 null。 */
    public ProcessInstanceEntity findById(String id) {
        List<ProcessInstanceEntity> results = jdbcTemplate.query(
                "SELECT * FROM process_instance WHERE id = ?",
                ROW_MAPPER, id);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 覆盖变量 JSON，仅允许未开始或运行中的实例更新。
     * JSON 的读取、浅合并和序列化由运行时 Service 负责。
     */
    public int updateVariablesJson(String id, String variablesJson) {
        return jdbcTemplate.update("UPDATE process_instance SET variables_json = ? WHERE id = ? "
                + "AND instance_status IN ('NOT_STARTED', 'RUNNING')", variablesJson, id);
    }

    /** 覆盖当前节点编码 JSON，仅允许未开始或运行中的实例更新。 */
    public int updateCurrentNodeCodes(String id, String currentNodeCodes) {
        return jdbcTemplate.update("UPDATE process_instance SET current_node_codes = ? WHERE id = ? "
                + "AND instance_status IN ('NOT_STARTED', 'RUNNING')", currentNodeCodes, id);
    }

    /** 将运行中的实例办结；重复办结或非运行态实例返回 0。 */
    public int complete(String id, LocalDateTime endedAt) {
        return jdbcTemplate.update("UPDATE process_instance SET instance_status = 'COMPLETED', ended_at = ? "
                        + "WHERE id = ? AND instance_status = 'RUNNING'",
                DefinitionRowMappers.toDbString(endedAt), id);
    }

    /** 分页查询指定发起人的流程实例。 */
    public List<ProcessInstanceEntity> queryStartedInstances(StartedInstanceQuery query, String starterUserId) {
        int pageNo = PageQueryNormalizer.normalizePageNo(query.getPageNo());
        int pageSize = PageQueryNormalizer.normalizePageSize(query.getPageSize());
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("SELECT * FROM process_instance WHERE starter_user_id = ? ");
        params.add(starterUserId);
        appendStartedFilters(sql, params, query);
        sql.append("ORDER BY started_at DESC, id DESC LIMIT ? OFFSET ?");
        params.add(Integer.valueOf(pageSize));
        params.add(Integer.valueOf((pageNo - 1) * pageSize));
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    /** 统计指定发起人的流程实例数量。 */
    public long countStartedInstances(StartedInstanceQuery query, String starterUserId) {
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(1) FROM process_instance WHERE starter_user_id = ? ");
        params.add(starterUserId);
        appendStartedFilters(sql, params, query);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count == null ? 0L : count.longValue();
    }

    private void appendStartedFilters(StringBuilder sql, List<Object> params, StartedInstanceQuery query) {
        if (!isBlank(query.getProcessCode())) {
            sql.append("AND process_code = ? ");
            params.add(query.getProcessCode());
        }
        if (!isBlank(query.getInstanceTitle())) {
            sql.append("AND LOWER(instance_title) LIKE ? ");
            params.add(like(query.getInstanceTitle()));
        }
        if (!isBlank(query.getBusinessKey())) {
            sql.append("AND business_key = ? ");
            params.add(query.getBusinessKey());
        }
        if (!isBlank(query.getInstanceStatus())) {
            sql.append("AND instance_status = ? ");
            params.add(query.getInstanceStatus());
        }
        if (!isBlank(query.getCurrentNodeCode())) {
            sql.append("AND current_node_codes IS NOT NULL "
                    + "AND EXISTS (SELECT 1 FROM json_each(current_node_codes) c WHERE c.value = ?) ");
            params.add(query.getCurrentNodeCode());
        }
        if (query.getStartedFrom() != null) {
            sql.append("AND started_at >= ? ");
            params.add(DefinitionRowMappers.toDbString(query.getStartedFrom()));
        }
        if (query.getStartedTo() != null) {
            sql.append("AND started_at <= ? ");
            params.add(DefinitionRowMappers.toDbString(query.getStartedTo()));
        }
    }

    private String like(String value) {
        return "%" + value.trim().toLowerCase() + "%";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

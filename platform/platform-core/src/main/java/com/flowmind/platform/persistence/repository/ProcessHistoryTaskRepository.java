package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.api.dto.CompletedTaskQuery;
import com.flowmind.platform.core.query.PageQueryNormalizer;
import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 历史任务仓储，统一访问 process_history_task。
 */
@Repository
public class ProcessHistoryTaskRepository {

    private static final RowMapper<ProcessHistoryTaskEntity> ROW_MAPPER =
            new RowMapper<ProcessHistoryTaskEntity>() {
                @Override
                public ProcessHistoryTaskEntity mapRow(ResultSet resultSet, int rowNum) throws SQLException {
                    ProcessHistoryTaskEntity entity = new ProcessHistoryTaskEntity();
                    entity.setId(resultSet.getString("id"));
                    entity.setInstanceId(resultSet.getString("instance_id"));
                    entity.setOperationId(resultSet.getString("operation_id"));
                    entity.setActiveTaskId(resultSet.getString("active_task_id"));
                    entity.setNodeCode(resultSet.getString("node_code"));
                    entity.setTaskGroupId(resultSet.getString("task_group_id"));
                    entity.setBranchKey(resultSet.getString("branch_key"));
                    entity.setAssigneeUserId(resultSet.getString("assignee_user_id"));
                    entity.setAssigneeUserName(resultSet.getString("assignee_user_name"));
                    entity.setDelegateFromUserId(resultSet.getString("delegate_from_user_id"));
                    entity.setDelegateFromUserName(resultSet.getString("delegate_from_user_name"));
                    entity.setHandleType(resultSet.getString("handle_type"));
                    entity.setActionType(resultSet.getString("action_type"));
                    entity.setCommentText(resultSet.getString("comment_text"));
                    entity.setVariablesSnapshot(resultSet.getString("variables_snapshot"));
                    entity.setStartedAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("started_at")));
                    entity.setCompletedAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("completed_at")));
                    entity.setExtraJson(resultSet.getString("extra_json"));
                    return entity;
                }
            };

    private final JdbcTemplate jdbcTemplate;

    public ProcessHistoryTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insert(ProcessHistoryTaskEntity entity) {
        return jdbcTemplate.update("INSERT INTO process_history_task "
                        + "(id, instance_id, operation_id, active_task_id, node_code, task_group_id, branch_key, "
                        + "assignee_user_id, assignee_user_name, delegate_from_user_id, delegate_from_user_name, "
                        + "handle_type, action_type, comment_text, variables_snapshot, started_at, completed_at, "
                        + "extra_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, 'NORMAL'), ?, ?, ?, ?, "
                        + "COALESCE(?, datetime('now')), ?)",
                entity.getId(),
                entity.getInstanceId(),
                entity.getOperationId(),
                entity.getActiveTaskId(),
                entity.getNodeCode(),
                entity.getTaskGroupId(),
                entity.getBranchKey(),
                entity.getAssigneeUserId(),
                entity.getAssigneeUserName(),
                entity.getDelegateFromUserId(),
                entity.getDelegateFromUserName(),
                entity.getHandleType(),
                entity.getActionType(),
                entity.getCommentText(),
                entity.getVariablesSnapshot(),
                DefinitionRowMappers.toDbString(entity.getStartedAt()),
                DefinitionRowMappers.toDbString(entity.getCompletedAt()),
                entity.getExtraJson());
    }

    public List<ProcessHistoryTaskEntity> findByInstanceId(String instanceId) {
        return jdbcTemplate.query("SELECT * FROM process_history_task WHERE instance_id = ? "
                        + "ORDER BY started_at ASC, completed_at ASC, id ASC",
                ROW_MAPPER, instanceId);
    }

    public List<ProcessHistoryTaskEntity> findByActiveTaskId(String activeTaskId) {
        return jdbcTemplate.query("SELECT * FROM process_history_task WHERE active_task_id = ? "
                        + "ORDER BY completed_at ASC, id ASC",
                ROW_MAPPER, activeTaskId);
    }

    public ProcessHistoryTaskEntity findByTaskActionOperation(String activeTaskId,
                                                              String actionType,
                                                              String operationId) {
        List<ProcessHistoryTaskEntity> results = jdbcTemplate.query(
                "SELECT * FROM process_history_task "
                        + "WHERE active_task_id = ? AND action_type = ? AND operation_id = ?",
                ROW_MAPPER, activeTaskId, actionType, operationId);
        return results.isEmpty() ? null : results.get(0);
    }

    public boolean existsByTaskActionOperation(String activeTaskId, String actionType, String operationId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM process_history_task "
                        + "WHERE active_task_id = ? AND action_type = ? AND operation_id = ?",
                Integer.class, activeTaskId, actionType, operationId);
        return count != null && count.intValue() > 0;
    }

    public List<ProcessHistoryTaskEntity> queryCompletedTasks(CompletedTaskQuery query) {
        CompletedTaskQuery normalized = query == null ? new CompletedTaskQuery() : query;
        int pageNo = PageQueryNormalizer.normalizePageNo(normalized.getPageNo());
        int pageSize = PageQueryNormalizer.normalizePageSize(normalized.getPageSize());
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("SELECT h.* FROM process_history_task h ");
        appendCompletedTaskWhere(sql, params, normalized);
        sql.append(" ORDER BY h.completed_at DESC, h.id DESC LIMIT ? OFFSET ?");
        params.add(Integer.valueOf(pageSize));
        params.add(Integer.valueOf((pageNo - 1) * pageSize));
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public long countCompletedTasks(CompletedTaskQuery query) {
        CompletedTaskQuery normalized = query == null ? new CompletedTaskQuery() : query;
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(1) FROM process_history_task h ");
        appendCompletedTaskWhere(sql, params, normalized);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count == null ? 0L : count.longValue();
    }

    public int deleteByInstanceId(String instanceId) {
        return jdbcTemplate.update("DELETE FROM process_history_task WHERE instance_id = ?", instanceId);
    }

    private void appendCompletedTaskWhere(StringBuilder sql, List<Object> params, CompletedTaskQuery query) {
        boolean joinedInstance = query.getProcessCode() != null && !query.getProcessCode().trim().isEmpty();
        if (joinedInstance) {
            sql.append("JOIN process_instance i ON i.id = h.instance_id ");
        }
        sql.append("WHERE 1 = 1 ");
        if (query.getUserId() != null && !query.getUserId().trim().isEmpty()) {
            sql.append("AND h.assignee_user_id = ? ");
            params.add(query.getUserId());
        }
        if (joinedInstance) {
            sql.append("AND i.process_code = ? ");
            params.add(query.getProcessCode());
        }
    }
}

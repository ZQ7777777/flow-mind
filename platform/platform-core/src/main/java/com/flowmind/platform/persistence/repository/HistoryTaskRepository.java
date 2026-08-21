package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 已归档任务的只增写入及稳定查询仓储。
 *
 * @author FlowMind
 * @since 2026-07-22
 */
@Repository
public class HistoryTaskRepository {

    private final JdbcTemplate jdbcTemplate;

    /** 创建历史任务仓储。 */
    public HistoryTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 插入已完成任务的不可变快照。 */
    public int insert(ProcessHistoryTaskEntity entity) {
        if (entity.getOperationId() == null || entity.getOperationId().trim().isEmpty()) {
            throw new IllegalArgumentException("operationId must not be empty");
        }
        return jdbcTemplate.update("INSERT INTO process_history_task "
                        + "(id, instance_id, operation_id, active_task_id, node_code, task_group_id, branch_key, "
                        + "assignee_user_id, assignee_user_name, delegate_from_user_id, delegate_from_user_name, "
                        + "handle_type, action_type, comment_text, variables_snapshot, started_at, completed_at, "
                        + "extra_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, 'NORMAL'), ?, ?, ?, ?, "
                        + "COALESCE(?, datetime('now')), ?)",
                entity.getId(), entity.getInstanceId(), entity.getOperationId(), entity.getActiveTaskId(),
                entity.getNodeCode(), entity.getTaskGroupId(), entity.getBranchKey(), entity.getAssigneeUserId(),
                entity.getAssigneeUserName(), entity.getDelegateFromUserId(), entity.getDelegateFromUserName(),
                entity.getHandleType(), entity.getActionType(), entity.getCommentText(), entity.getVariablesSnapshot(),
                DefinitionRowMappers.toDbString(entity.getStartedAt()),
                DefinitionRowMappers.toDbString(entity.getCompletedAt()), entity.getExtraJson());
    }

    /** 按完成时间和 ID 稳定读取一个实例的历史任务。 */
    public List<ProcessHistoryTaskEntity> findByInstanceId(String instanceId) {
        return jdbcTemplate.query("SELECT * FROM process_history_task WHERE instance_id = ? "
                        + "ORDER BY completed_at ASC, id ASC", RuntimeRowMappers.HISTORY_TASK, instanceId);
    }

    /** 按完成时间和 ID 稳定读取面向用户展示的实例历史任务。 */
    public List<ProcessHistoryTaskEntity> findVisibleByInstanceId(String instanceId) {
        return jdbcTemplate.query("SELECT h.* FROM process_history_task h WHERE h.instance_id = ? "
                        + HistoryVisibilitySql.PREDICATE
                        + "ORDER BY h.completed_at ASC, h.id ASC",
                RuntimeRowMappers.HISTORY_TASK, instanceId);
    }
}

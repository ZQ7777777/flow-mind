package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 活动任务的原子条件更新仓储。
 *
 * <p>所有方法返回受影响行数。返回 1 表示取得任务修改权；返回 0 表示任务不存在、
 * 状态不允许或版本已过期，调用方应映射为 {@link RepositoryConflictCodes#TASK_CONCURRENT_MODIFIED}。</p>
 */
@Repository
public class ActiveTaskRepository {

    private static final RowMapper<ProcessActiveTaskEntity> ROW_MAPPER =
            new RowMapper<ProcessActiveTaskEntity>() {
                @Override
                public ProcessActiveTaskEntity mapRow(ResultSet resultSet, int rowNum) throws SQLException {
                    ProcessActiveTaskEntity entity = new ProcessActiveTaskEntity();
                    entity.setId(resultSet.getString("id"));
                    entity.setInstanceId(resultSet.getString("instance_id"));
                    entity.setDefinitionId(resultSet.getString("definition_id"));
                    entity.setNodeCode(resultSet.getString("node_code"));
                    entity.setCandidateUserIds(resultSet.getString("candidate_user_ids"));
                    entity.setAssigneeUserId(resultSet.getString("assignee_user_id"));
                    entity.setAssigneeUserName(resultSet.getString("assignee_user_name"));
                    entity.setDelegateFromUserId(resultSet.getString("delegate_from_user_id"));
                    entity.setTaskStatus(resultSet.getString("task_status"));
                    entity.setTaskGroupId(resultSet.getString("task_group_id"));
                    entity.setBranchKey(resultSet.getString("branch_key"));
                    entity.setLockVersion(Long.valueOf(resultSet.getLong("lock_version")));
                    entity.setCreatedAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("created_at")));
                    entity.setDueAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("due_at")));
                    return entity;
                }
            };

    private final JdbcTemplate jdbcTemplate;

    /** 创建活动任务仓储。 */
    public ActiveTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 将 ACTIVE 或 CLAIMED 任务原子标记为 COMPLETED。 */
    public int complete(String id, long expectedLockVersion) {
        return updateTerminalStatus(id, expectedLockVersion, "COMPLETED");
    }

    /** 按活动任务 ID 读取完整任务上下文；不存在时返回 null。 */
    public ProcessActiveTaskEntity findById(String id) {
        List<ProcessActiveTaskEntity> results = jdbcTemplate.query(
                "SELECT * FROM process_active_task WHERE id = ?",
                stringParam(id), ROW_MAPPER);
        return results.isEmpty() ? null : results.get(0);
    }

    /** 按实例 ID 读取活动任务，按创建时间和 ID 稳定排序。 */
    public List<ProcessActiveTaskEntity> findByInstanceId(String instanceId) {
        return jdbcTemplate.query("SELECT * FROM process_active_task "
                        + "WHERE instance_id = ? ORDER BY created_at ASC, id ASC",
                stringParam(instanceId), ROW_MAPPER);
    }

    /** 将 ACTIVE 或 CLAIMED 任务原子标记为 CANCELED。 */
    public int cancel(String id, long expectedLockVersion) {
        return updateTerminalStatus(id, expectedLockVersion, "CANCELED");
    }

    /** 将 ACTIVE 任务原子认领给指定用户。 */
    public int claim(String id, long expectedLockVersion, String assigneeUserId, String assigneeUserName) {
        return jdbcTemplate.update(
                "UPDATE process_active_task "
                        + "SET task_status = 'CLAIMED', assignee_user_id = ?, assignee_user_name = ?, "
                        + "lock_version = lock_version + 1 "
                        + "WHERE id = ? AND task_status = 'ACTIVE' AND lock_version = ?",
                assigneeUserId, assigneeUserName, id, expectedLockVersion);
    }

    /** 将 CLAIMED 任务原子取消认领并恢复为 ACTIVE。 */
    public int unclaim(String id, long expectedLockVersion) {
        return jdbcTemplate.update(
                "UPDATE process_active_task "
                        + "SET task_status = 'ACTIVE', assignee_user_id = NULL, assignee_user_name = NULL, "
                        + "lock_version = lock_version + 1 "
                        + "WHERE id = ? AND task_status = 'CLAIMED' AND lock_version = ?",
                id, expectedLockVersion);
    }

    /** 在 ACTIVE 或 CLAIMED 状态下原子变更任务办理人。 */
    public int transfer(String id, long expectedLockVersion, String assigneeUserId, String assigneeUserName) {
        return jdbcTemplate.update(
                "UPDATE process_active_task "
                        + "SET assignee_user_id = ?, assignee_user_name = ?, lock_version = lock_version + 1 "
                        + "WHERE id = ? AND task_status IN ('ACTIVE', 'CLAIMED') AND lock_version = ?",
                assigneeUserId, assigneeUserName, id, expectedLockVersion);
    }

    private int updateTerminalStatus(String id, long expectedLockVersion, String targetStatus) {
        return jdbcTemplate.update(
                "UPDATE process_active_task SET task_status = ?, lock_version = lock_version + 1 "
                        + "WHERE id = ? AND task_status IN ('ACTIVE', 'CLAIMED') AND lock_version = ?",
                targetStatus, id, expectedLockVersion);
    }

    private PreparedStatementSetter stringParam(String value) {
        return preparedStatement -> preparedStatement.setString(1, value);
    }
}

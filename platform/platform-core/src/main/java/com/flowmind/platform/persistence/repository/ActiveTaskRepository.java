package com.flowmind.platform.persistence.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 活动任务的原子条件更新仓储。
 *
 * <p>所有方法返回受影响行数。返回 1 表示取得任务修改权；返回 0 表示任务不存在、
 * 状态不允许或版本已过期，调用方应映射为 {@link RepositoryConflictCodes#TASK_CONCURRENT_MODIFIED}。</p>
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 */
@Repository
public class ActiveTaskRepository {

    private final JdbcTemplate jdbcTemplate;

    /** 创建活动任务仓储。 */
    public ActiveTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 将 ACTIVE 或 CLAIMED 任务原子标记为 COMPLETED。 */
    public int complete(String id, long expectedLockVersion) {
        return updateTerminalStatus(id, expectedLockVersion, "COMPLETED");
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
}

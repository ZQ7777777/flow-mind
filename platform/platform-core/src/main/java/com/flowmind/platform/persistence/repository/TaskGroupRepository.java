package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.persistence.entity.ProcessTaskGroupEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 任务组计数、状态和并行分支到达的原子条件更新仓储。
 *
 * <p>更新方法返回 1 表示成功，返回 0 表示状态、版本、计数上限或分支状态不满足。
 * Repository 不进行重试；调用方有限重试耗尽后应返回
 * {@link RepositoryConflictCodes#TASK_GROUP_CONCURRENT_MODIFIED}。</p>
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 */
@Repository
public class TaskGroupRepository {

    private final JdbcTemplate jdbcTemplate;

    /** 创建任务组仓储。 */
    public TaskGroupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 插入会签、或签或并行网关任务组。 */
    public int insert(ProcessTaskGroupEntity entity) {
        return jdbcTemplate.update("INSERT INTO process_task_group "
                        + "(id, instance_id, node_code, join_node_code, parent_group_id, parent_branch_key, "
                        + "group_type, total_count, completed_count, branch_state_json, group_status, "
                        + "lock_version, created_at, completed_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, 0), ?, COALESCE(?, 'ACTIVE'), "
                        + "COALESCE(?, 0), COALESCE(?, datetime('now')), ?)",
                entity.getId(), entity.getInstanceId(), entity.getNodeCode(), entity.getJoinNodeCode(),
                entity.getParentGroupId(), entity.getParentBranchKey(), entity.getGroupType(),
                entity.getTotalCount(), entity.getCompletedCount(), entity.getBranchStateJson(),
                entity.getGroupStatus(), entity.getLockVersion(),
                DefinitionRowMappers.toDbString(entity.getCreatedAt()),
                DefinitionRowMappers.toDbString(entity.getCompletedAt()));
    }

    /** 按任务组 ID 查询；不存在时返回 {@code null}。 */
    public ProcessTaskGroupEntity findById(String id) {
        List<ProcessTaskGroupEntity> results = jdbcTemplate.query(
                "SELECT * FROM process_task_group WHERE id = ?", RuntimeRowMappers.TASK_GROUP, id);
        return results.isEmpty() ? null : results.get(0);
    }

    /** 统计实例下仍处于 ACTIVE 状态的任务组数量。 */
    public long countActiveByInstanceId(String instanceId) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM process_task_group "
                        + "WHERE instance_id = ? AND group_status = 'ACTIVE'", Long.class, instanceId);
        return count == null ? 0L : count.longValue();
    }

    /** 按创建时间和 ID 稳定读取实例下所有活动任务组。 */
    public List<ProcessTaskGroupEntity> findActiveByInstanceId(String instanceId) {
        return jdbcTemplate.query("SELECT * FROM process_task_group WHERE instance_id = ? "
                        + "AND group_status = 'ACTIVE' ORDER BY created_at ASC, id ASC",
                RuntimeRowMappers.TASK_GROUP, instanceId);
    }

    /**
     * 原子增加会签完成计数；达到 total_count 时同时完成任务组。
     */
    public int incrementCompletedCount(String id, long expectedLockVersion) {
        return jdbcTemplate.update(
                "UPDATE process_task_group "
                        + "SET completed_count = completed_count + 1, "
                        + "group_status = CASE WHEN completed_count + 1 = total_count "
                        + "THEN 'COMPLETED' ELSE group_status END, "
                        + "completed_at = CASE WHEN completed_count + 1 = total_count "
                        + "THEN datetime('now') ELSE completed_at END, "
                        + "lock_version = lock_version + 1 "
                        + "WHERE id = ? AND group_status = 'ACTIVE' "
                        + "AND completed_count < total_count AND lock_version = ?",
                id, expectedLockVersion);
    }

    /** 按任务组 ID 读取完整任务组上下文；不存在时返回 null。 */
    /**
     * 原子标记并行分支到达并增加汇聚计数。
     *
     * <p>仅当 branch_state_json 中指定 branchKey 的值为 RUNNING 时更新，因此同一分支
     * 重复到达不会重复计数。branchKey 对应 JSON 对象的一级属性名。</p>
     */
    /** 或签任务组首个通过者获胜后，原子完成任务组并记录一次完成计数。 */
    public int completeOrSignGroup(String id, long expectedLockVersion) {
        return jdbcTemplate.update(
                "UPDATE process_task_group "
                        + "SET completed_count = 1, group_status = 'COMPLETED', "
                        + "completed_at = datetime('now'), lock_version = lock_version + 1 "
                        + "WHERE id = ? AND group_type = 'OR_SIGN' AND group_status = 'ACTIVE' "
                        + "AND completed_count = 0 AND total_count > 0 AND lock_version = ?",
                id, expectedLockVersion);
    }

    /**
     * 原子标记并行分支到达并增加汇聚计数。
     *
     * <p>仅当 branch_state_json 中指定 branchKey 的值为 RUNNING 时更新，因此同一分支重复到达不会重复计数。
     * branchKey 对应 JSON 对象的一级属性名。</p>
     */
    public int markBranchArrived(String id, String branchKey, long expectedLockVersion) {
        String branchPath = toJsonPath(branchKey);
        return jdbcTemplate.update(
                "UPDATE process_task_group "
                        + "SET branch_state_json = json_set(branch_state_json, ?, 'ARRIVED'), "
                        + "completed_count = completed_count + 1, "
                        + "group_status = CASE WHEN completed_count + 1 = total_count "
                        + "THEN 'COMPLETED' ELSE group_status END, "
                        + "completed_at = CASE WHEN completed_count + 1 = total_count "
                        + "THEN datetime('now') ELSE completed_at END, "
                        + "lock_version = lock_version + 1 "
                        + "WHERE id = ? AND group_status = 'ACTIVE' "
                        + "AND completed_count < total_count AND lock_version = ? "
                        + "AND json_extract(branch_state_json, ?) = 'RUNNING'",
                branchPath, id, expectedLockVersion, branchPath);
    }

    /** 按任务组 ID 读取并行分支状态 JSON；不存在时返回 null。 */
    public String findBranchStateJson(String id) {
        return jdbcTemplate.query(
                "SELECT branch_state_json FROM process_task_group WHERE id = ?",
                resultSet -> resultSet.next() ? resultSet.getString(1) : null,
                id);
    }

    /** 将 ACTIVE 任务组原子标记为 COMPLETED。 */
    public int complete(String id, long expectedLockVersion) {
        return updateStatus(id, expectedLockVersion, "COMPLETED");
    }

    /** 将 ACTIVE 任务组原子标记为 CANCELED。 */
    public int cancel(String id, long expectedLockVersion) {
        return updateStatus(id, expectedLockVersion, "CANCELED");
    }

    private int updateStatus(String id, long expectedLockVersion, String targetStatus) {
        return jdbcTemplate.update(
                "UPDATE process_task_group "
                        + "SET group_status = ?, completed_at = CASE WHEN ? = 'COMPLETED' "
                        + "THEN datetime('now') ELSE completed_at END, lock_version = lock_version + 1 "
                        + "WHERE id = ? AND group_status = 'ACTIVE' AND lock_version = ?",
                targetStatus, targetStatus, id, expectedLockVersion);
    }

    private String toJsonPath(String branchKey) {
        if (branchKey == null || branchKey.isEmpty()) {
            throw new IllegalArgumentException("branchKey must not be empty");
        }
        return "$.\"" + branchKey.replace("\"", "\\\"") + "\"";
    }
}

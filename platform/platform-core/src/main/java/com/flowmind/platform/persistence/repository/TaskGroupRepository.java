package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.persistence.entity.ProcessTaskGroupEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
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

    private static final RowMapper<ProcessTaskGroupEntity> ROW_MAPPER =
            new RowMapper<ProcessTaskGroupEntity>() {
                @Override
                public ProcessTaskGroupEntity mapRow(ResultSet resultSet, int rowNum) throws SQLException {
                    ProcessTaskGroupEntity entity = new ProcessTaskGroupEntity();
                    entity.setId(resultSet.getString("id"));
                    entity.setInstanceId(resultSet.getString("instance_id"));
                    entity.setNodeCode(resultSet.getString("node_code"));
                    entity.setJoinNodeCode(resultSet.getString("join_node_code"));
                    entity.setParentGroupId(resultSet.getString("parent_group_id"));
                    entity.setParentBranchKey(resultSet.getString("parent_branch_key"));
                    entity.setGroupType(resultSet.getString("group_type"));
                    entity.setTotalCount(Integer.valueOf(resultSet.getInt("total_count")));
                    entity.setCompletedCount(Integer.valueOf(resultSet.getInt("completed_count")));
                    entity.setBranchStateJson(resultSet.getString("branch_state_json"));
                    entity.setGroupStatus(resultSet.getString("group_status"));
                    entity.setLockVersion(Long.valueOf(resultSet.getLong("lock_version")));
                    entity.setCreatedAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("created_at")));
                    entity.setCompletedAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("completed_at")));
                    return entity;
                }
            };

    private final JdbcTemplate jdbcTemplate;

    /** 创建任务组仓储。 */
    public TaskGroupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
    public ProcessTaskGroupEntity findById(String id) {
        List<ProcessTaskGroupEntity> results = jdbcTemplate.query(
                "SELECT * FROM process_task_group WHERE id = ?",
                ROW_MAPPER, id);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 原子标记并行分支到达并增加汇聚计数。
     *
     * <p>仅当 branch_state_json 中指定 branchKey 的值为 RUNNING 时更新，因此同一分支
     * 重复到达不会重复计数。branchKey 对应 JSON 对象的一级属性名。</p>
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

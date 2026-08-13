package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.api.dto.AdminTaskQuery;
import com.flowmind.platform.api.dto.TodoTaskQuery;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.TaskQueryEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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
                    entity.setDelegateFromUserName(resultSet.getString("delegate_from_user_name"));
                    entity.setTaskStatus(resultSet.getString("task_status"));
                    entity.setTaskGroupId(resultSet.getString("task_group_id"));
                    entity.setBranchKey(resultSet.getString("branch_key"));
                    entity.setLockVersion(Long.valueOf(resultSet.getLong("lock_version")));
                    entity.setCreatedAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("created_at")));
                    entity.setDueAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("due_at")));
                    return entity;
                }
            };

    private static final RowMapper<TaskQueryEntity> QUERY_ROW_MAPPER =
            new RowMapper<TaskQueryEntity>() {
                @Override
                public TaskQueryEntity mapRow(ResultSet resultSet, int rowNum) throws SQLException {
                    TaskQueryEntity entity = new TaskQueryEntity();
                    entity.setTaskId(resultSet.getString("task_id"));
                    entity.setInstanceId(resultSet.getString("instance_id"));
                    entity.setDefinitionId(resultSet.getString("definition_id"));
                    entity.setProcessCode(resultSet.getString("process_code"));
                    entity.setProcessName(resultSet.getString("process_name"));
                    entity.setInstanceTitle(resultSet.getString("instance_title"));
                    entity.setStarterUserId(resultSet.getString("starter_user_id"));
                    entity.setStarterUserName(resultSet.getString("starter_user_name"));
                    entity.setNodeCode(resultSet.getString("node_code"));
                    entity.setNodeName(resultSet.getString("node_name"));
                    entity.setCandidateUserIds(resultSet.getString("candidate_user_ids"));
                    entity.setAssigneeUserId(resultSet.getString("assignee_user_id"));
                    entity.setAssigneeUserName(resultSet.getString("assignee_user_name"));
                    entity.setDelegateFromUserId(resultSet.getString("delegate_from_user_id"));
                    entity.setDelegateFromUserName(resultSet.getString("delegate_from_user_name"));
                    entity.setTaskGroupId(resultSet.getString("task_group_id"));
                    entity.setBranchKey(resultSet.getString("branch_key"));
                    entity.setTaskStatus(resultSet.getString("task_status"));
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

    /** 插入一条运行中的任务记录。 */
    public int insert(ProcessActiveTaskEntity entity) {
        return jdbcTemplate.update("INSERT INTO process_active_task "
                        + "(id, instance_id, definition_id, node_code, candidate_user_ids, assignee_user_id, "
                        + "assignee_user_name, delegate_from_user_id, delegate_from_user_name, task_status, "
                        + "task_group_id, branch_key, lock_version, created_at, due_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, 'ACTIVE'), ?, ?, "
                        + "COALESCE(?, 0), COALESCE(?, datetime('now')), ?)",
                entity.getId(), entity.getInstanceId(), entity.getDefinitionId(), entity.getNodeCode(),
                entity.getCandidateUserIds(), entity.getAssigneeUserId(), entity.getAssigneeUserName(),
                entity.getDelegateFromUserId(), entity.getDelegateFromUserName(), entity.getTaskStatus(),
                entity.getTaskGroupId(), entity.getBranchKey(), entity.getLockVersion(),
                DefinitionRowMappers.toDbString(entity.getCreatedAt()),
                DefinitionRowMappers.toDbString(entity.getDueAt()));
    }

    /** 按任务 ID 查询；不存在时返回 {@code null}。 */
    public ProcessActiveTaskEntity findById(String id) {
        List<ProcessActiveTaskEntity> results = jdbcTemplate.query(
                "SELECT * FROM process_active_task WHERE id = ?", RuntimeRowMappers.ACTIVE_TASK, id);
        return results.isEmpty() ? null : results.get(0);
    }

    /** 按创建时间和 ID 稳定读取实例下的待办或已认领任务。 */
    public List<ProcessActiveTaskEntity> findOpenByInstanceId(String instanceId) {
        return jdbcTemplate.query("SELECT * FROM process_active_task WHERE instance_id = ? "
                        + "AND task_status IN ('ACTIVE', 'CLAIMED') ORDER BY created_at ASC, id ASC",
                RuntimeRowMappers.ACTIVE_TASK, instanceId);
    }

    /** 按创建时间和 ID 稳定读取任务组内仍开放的活动任务。 */
    public List<ProcessActiveTaskEntity> findOpenByTaskGroupId(String taskGroupId) {
        return jdbcTemplate.query("SELECT * FROM process_active_task WHERE task_group_id = ? "
                        + "AND task_status IN ('ACTIVE', 'CLAIMED') ORDER BY created_at ASC, id ASC",
                RuntimeRowMappers.ACTIVE_TASK, taskGroupId);
    }

    /** 按任务组读取仍开放的待办，可排除已获胜或已处理的当前任务。 */
    public List<ProcessActiveTaskEntity> findOpenByTaskGroupId(String taskGroupId, String excludedTaskId) {
        return jdbcTemplate.query("SELECT * FROM process_active_task WHERE task_group_id = ? "
                        + "AND (? IS NULL OR id <> ?) "
                        + "AND task_status IN ('ACTIVE', 'CLAIMED') ORDER BY created_at ASC, id ASC",
                RuntimeRowMappers.ACTIVE_TASK, taskGroupId, emptyToNull(excludedTaskId), emptyToNull(excludedTaskId));
    }

    /** Read open tasks directly under a parallel group or under its child approval groups. */
    public List<ProcessActiveTaskEntity> findOpenByParallelContext(String parallelGroupId, String excludedTaskId) {
        return jdbcTemplate.query("SELECT t.* FROM process_active_task t "
                        + "LEFT JOIN process_task_group g ON g.id = t.task_group_id "
                        + "WHERE (t.task_group_id = ? OR g.parent_group_id = ?) "
                        + "AND (? IS NULL OR t.id <> ?) "
                        + "AND t.task_status IN ('ACTIVE', 'CLAIMED') "
                        + "ORDER BY t.created_at ASC, t.id ASC",
                RuntimeRowMappers.ACTIVE_TASK, parallelGroupId, parallelGroupId,
                emptyToNull(excludedTaskId), emptyToNull(excludedTaskId));
    }

    /** 按到期时间读取超时的开放任务。 */
    public List<ProcessActiveTaskEntity> findTimeoutOpenTasks(java.time.LocalDateTime scanAt, int limit) {
        if (scanAt == null || limit <= 0) {
            return java.util.Collections.emptyList();
        }
        return jdbcTemplate.query("SELECT * FROM process_active_task "
                        + "WHERE due_at IS NOT NULL AND due_at <= ? AND task_status IN ('ACTIVE', 'CLAIMED') "
                        + "ORDER BY due_at ASC, created_at ASC, id ASC LIMIT ?",
                RuntimeRowMappers.ACTIVE_TASK,
                DefinitionRowMappers.toDbString(scanAt),
                Integer.valueOf(limit));
    }

    /** 按节点提醒提前量读取即将到期的开放任务。 */
    public List<ProcessActiveTaskEntity> findDueSoonOpenTasks(java.time.LocalDateTime scanAt, int limit) {
        if (scanAt == null || limit <= 0) {
            return java.util.Collections.emptyList();
        }
        String configuredBefore = "COALESCE(CAST(json_extract(n.reminder_config, '$.beforeDueMinutes') AS INTEGER), 30)";
        String timeoutDuration = "COALESCE(CAST(json_extract(n.timeout_config, '$.durationMinutes') AS INTEGER), 0)";
        String effectiveBefore = "CASE WHEN " + timeoutDuration + " > 0 AND " + configuredBefore + " > "
                + timeoutDuration + " THEN " + timeoutDuration + " ELSE " + configuredBefore + " END";
        return jdbcTemplate.query("SELECT * FROM (SELECT t.*, " + effectiveBefore + " AS reminder_before_minutes "
                        + "FROM process_active_task t "
                        + "JOIN process_node n ON n.definition_id = t.definition_id AND n.node_code = t.node_code "
                        + "WHERE t.due_at IS NOT NULL AND t.due_at > ? "
                        + "AND t.task_status IN ('ACTIVE', 'CLAIMED') "
                        + "AND (json_extract(n.reminder_config, '$.enabled') = 1 "
                        + "OR LOWER(CAST(json_extract(n.reminder_config, '$.enabled') AS TEXT)) = 'true')) due "
                        + "WHERE reminder_before_minutes > 0 "
                        + "AND datetime(due_at, '-' || reminder_before_minutes || ' minutes') <= datetime(?) "
                        + "ORDER BY due_at ASC, created_at ASC, id ASC LIMIT ?",
                RuntimeRowMappers.ACTIVE_TASK,
                DefinitionRowMappers.toDbString(scanAt),
                DefinitionRowMappers.toDbString(scanAt),
                Integer.valueOf(limit));
    }
    /** 统计实例下状态为 ACTIVE 或 CLAIMED 的任务数量。 */
    public long countOpenByInstanceId(String instanceId) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM process_active_task "
                        + "WHERE instance_id = ? AND task_status IN ('ACTIVE', 'CLAIMED')",
                Long.class, instanceId);
        return count == null ? 0L : count.longValue();
    }

    /** 将 ACTIVE 或 CLAIMED 任务原子标记为 COMPLETED。 */
    public int complete(String id, long expectedLockVersion) {
        return updateTerminalStatus(id, expectedLockVersion, "COMPLETED");
    }

    /** 按实例 ID 读取活动任务，按创建时间和 ID 稳定排序。 */
    public List<ProcessActiveTaskEntity> findByInstanceId(String instanceId) {
        return jdbcTemplate.query("SELECT * FROM process_active_task "
                        + "WHERE instance_id = ? ORDER BY created_at ASC, id ASC",
                ROW_MAPPER, instanceId);
    }

    /** 查询当前用户的待办任务读模型。 */
    public List<TaskQueryEntity> queryTodoTasks(TodoTaskQuery query,
                                                String currentUserId) {
        int pageNo = com.flowmind.platform.core.query.PageQueryNormalizer.normalizePageNo(query.getPageNo());
        int pageSize = com.flowmind.platform.core.query.PageQueryNormalizer.normalizePageSize(query.getPageSize());
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder();
        appendTodoCte(sql, params, currentUserId);
        sql.append("SELECT * FROM ranked WHERE rn = 1 ");
        appendTodoFilters(sql, params, query, currentUserId);
        appendTodoOrder(sql, query);
        sql.append(" LIMIT ? OFFSET ?");
        params.add(Integer.valueOf(pageSize));
        params.add(Integer.valueOf((pageNo - 1) * pageSize));
        return jdbcTemplate.query(sql.toString(), QUERY_ROW_MAPPER, params.toArray());
    }

    /** 统计当前用户待办任务数量。 */
    public long countTodoTasks(TodoTaskQuery query, String currentUserId) {
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder();
        appendTodoCte(sql, params, currentUserId);
        sql.append("SELECT COUNT(1) FROM ranked WHERE rn = 1 ");
        appendTodoFilters(sql, params, query, currentUserId);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count == null ? 0L : count.longValue();
    }

    /** 按任务 ID 读取活动任务读模型；不存在时返回 {@code null}。 */
    public TaskQueryEntity queryTaskById(String taskId) {
        StringBuilder sql = new StringBuilder();
        appendAdminTaskSelect(sql);
        sql.append("WHERE t.id = ? AND t.task_status IN ('ACTIVE', 'CLAIMED')");
        List<TaskQueryEntity> results = jdbcTemplate.query(sql.toString(), QUERY_ROW_MAPPER, taskId);
        return results.isEmpty() ? null : results.get(0);
    }

    /** 按实例 ID 读取开放活动任务读模型。 */
    public List<TaskQueryEntity> queryOpenTasksByInstanceId(String instanceId) {
        return jdbcTemplate.query("SELECT t.id AS task_id, t.instance_id, t.definition_id, "
                        + "i.process_code, i.process_name, i.instance_title, i.starter_user_id, "
                        + "i.starter_user_name, t.node_code, n.node_name, t.candidate_user_ids, "
                        + "t.assignee_user_id, t.assignee_user_name, t.delegate_from_user_id, "
                        + "t.delegate_from_user_name, t.task_group_id, t.branch_key, t.task_status, "
                        + "t.lock_version, t.created_at, t.due_at "
                        + "FROM process_active_task t "
                        + "JOIN process_instance i ON i.id = t.instance_id "
                        + "LEFT JOIN process_node n ON n.definition_id = t.definition_id AND n.node_code = t.node_code "
                        + "WHERE t.instance_id = ? AND t.task_status IN ('ACTIVE', 'CLAIMED') "
                        + "ORDER BY t.created_at ASC, t.id ASC",
                QUERY_ROW_MAPPER, instanceId);
    }

    /** Admin-side active task page query. */
    public List<TaskQueryEntity> queryAdminActiveTasks(AdminTaskQuery query) {
        AdminTaskQuery normalized = query == null ? new AdminTaskQuery() : query;
        int pageNo = com.flowmind.platform.core.query.PageQueryNormalizer.normalizePageNo(normalized.getPageNo());
        int pageSize = com.flowmind.platform.core.query.PageQueryNormalizer.normalizePageSize(normalized.getPageSize());
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder();
        appendAdminTaskSelect(sql);
        appendAdminTaskWhere(sql, params, normalized);
        sql.append(" ORDER BY CASE WHEN t.due_at IS NULL THEN 1 ELSE 0 END ASC, "
                + "t.due_at ASC, t.created_at DESC, t.id DESC LIMIT ? OFFSET ?");
        params.add(Integer.valueOf(pageSize));
        params.add(Integer.valueOf((pageNo - 1) * pageSize));
        return jdbcTemplate.query(sql.toString(), QUERY_ROW_MAPPER, params.toArray());
    }

    /** Count records for admin-side active task query. */
    public long countAdminActiveTasks(AdminTaskQuery query) {
        AdminTaskQuery normalized = query == null ? new AdminTaskQuery() : query;
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(1) FROM process_active_task t "
                + "JOIN process_instance i ON i.id = t.instance_id "
                + "LEFT JOIN process_node n ON n.definition_id = t.definition_id AND n.node_code = t.node_code ");
        appendAdminTaskWhere(sql, params, normalized);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count == null ? 0L : count.longValue();
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

    /** 判断同一个活动或签组内是否已有其他任务被认领。 */
    public boolean hasClaimedSiblingInActiveOrSignGroup(String taskId, String taskGroupId) {
        if (isBlank(taskId) || isBlank(taskGroupId)) {
            return false;
        }
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM process_active_task s "
                        + "JOIN process_task_group g ON g.id = s.task_group_id "
                        + "WHERE s.task_group_id = ? AND s.id <> ? AND s.task_status = 'CLAIMED' "
                        + "AND g.group_type = 'OR_SIGN' AND g.group_status = 'ACTIVE'",
                Long.class, taskGroupId, taskId);
        return count != null && count.longValue() > 0L;
    }

    /** 在 ACTIVE 或 CLAIMED 状态下原子变更任务办理人。 */
    public int transfer(String id, long expectedLockVersion, String assigneeUserId, String assigneeUserName) {
        return jdbcTemplate.update(
                "UPDATE process_active_task "
                        + "SET assignee_user_id = ?, assignee_user_name = ?, lock_version = lock_version + 1 "
                        + "WHERE id = ? AND task_status IN ('ACTIVE', 'CLAIMED') AND lock_version = ?",
                assigneeUserId, assigneeUserName, id, expectedLockVersion);
    }

    /** Mark an open task as delegated to the target assignee. */
    public int delegateTask(String id, long expectedLockVersion, String assigneeUserId,
                            String assigneeUserName, String delegateFromUserId, String delegateFromUserName) {
        return jdbcTemplate.update(
                "UPDATE process_active_task "
                        + "SET assignee_user_id = ?, assignee_user_name = ?, delegate_from_user_id = ?, "
                        + "delegate_from_user_name = ?, "
                        + "lock_version = lock_version + 1 "
                        + "WHERE id = ? AND task_status IN ('ACTIVE', 'CLAIMED') AND lock_version = ?",
                assigneeUserId, assigneeUserName, delegateFromUserId, delegateFromUserName, id, expectedLockVersion);
    }

    private int updateTerminalStatus(String id, long expectedLockVersion, String targetStatus) {
        return jdbcTemplate.update(
                "UPDATE process_active_task SET task_status = ?, lock_version = lock_version + 1 "
                        + "WHERE id = ? AND task_status IN ('ACTIVE', 'CLAIMED') AND lock_version = ?",
                targetStatus, id, expectedLockVersion);
    }

    private void appendTodoCte(StringBuilder sql,
                               List<Object> params,
                               String currentUserId) {
        sql.append("WITH candidate_sources AS (");
        appendTodoSourceSelect(sql, "1");
        sql.append(" WHERE t.assignee_user_id = ? AND t.task_status IN ('ACTIVE', 'CLAIMED') ");
        params.add(currentUserId);
        sql.append("UNION ALL ");
        appendTodoSourceSelect(sql, "2");
        sql.append(" WHERE t.task_status = 'ACTIVE' AND t.assignee_user_id IS NULL AND t.candidate_user_ids IS NOT NULL "
                + "AND EXISTS (SELECT 1 FROM json_each(t.candidate_user_ids) c WHERE c.value = ?) "
                + "AND NOT EXISTS (SELECT 1 FROM process_active_task s "
                + "JOIN process_task_group g ON g.id = s.task_group_id "
                + "WHERE s.task_group_id = t.task_group_id AND s.id <> t.id "
                + "AND s.task_status = 'CLAIMED' AND g.group_type = 'OR_SIGN' "
                + "AND g.group_status = 'ACTIVE') ");
        params.add(currentUserId);
        sql.append("), ranked AS (SELECT *, ROW_NUMBER() OVER (PARTITION BY task_id "
                + "ORDER BY source_priority ASC) AS rn FROM candidate_sources) ");
    }

    private void appendTodoSourceSelect(StringBuilder sql,
                                        String sourcePriority) {
        sql.append("SELECT ").append(sourcePriority).append(" AS source_priority, ")
                .append("t.id AS task_id, t.instance_id, t.definition_id, i.process_code, i.process_name, ")
                .append("i.instance_title, i.starter_user_id, i.starter_user_name, t.node_code, n.node_name, ")
                .append("t.candidate_user_ids, t.assignee_user_id, t.assignee_user_name, ")
                .append("t.delegate_from_user_id, t.delegate_from_user_name, ")
                .append("t.task_group_id, t.branch_key, t.task_status, t.lock_version, t.created_at, t.due_at ")
                .append("FROM process_active_task t ")
                .append("JOIN process_instance i ON i.id = t.instance_id ")
                .append("LEFT JOIN process_node n ON n.definition_id = t.definition_id AND n.node_code = t.node_code ");
    }

    private void appendAdminTaskSelect(StringBuilder sql) {
        sql.append("SELECT t.id AS task_id, t.instance_id, t.definition_id, "
                + "i.process_code, i.process_name, i.instance_title, i.starter_user_id, "
                + "i.starter_user_name, t.node_code, n.node_name, t.candidate_user_ids, "
                + "t.assignee_user_id, t.assignee_user_name, t.delegate_from_user_id, "
                + "t.delegate_from_user_name, t.task_group_id, t.branch_key, t.task_status, "
                + "t.lock_version, t.created_at, t.due_at "
                + "FROM process_active_task t "
                + "JOIN process_instance i ON i.id = t.instance_id "
                + "LEFT JOIN process_node n ON n.definition_id = t.definition_id AND n.node_code = t.node_code ");
    }

    private void appendAdminTaskWhere(StringBuilder sql, List<Object> params, AdminTaskQuery query) {
        sql.append("WHERE 1 = 1 ");
        if (!isBlank(query.getInstanceId())) {
            sql.append("AND t.instance_id = ? ");
            params.add(query.getInstanceId());
        }
        if (!isBlank(query.getProcessCode())) {
            sql.append("AND i.process_code = ? ");
            params.add(query.getProcessCode());
        }
        if (!isBlank(query.getNodeCode())) {
            sql.append("AND t.node_code = ? ");
            params.add(query.getNodeCode());
        }
        if (query.getTaskStatus() != null) {
            sql.append("AND t.task_status = ? ");
            params.add(query.getTaskStatus().name());
        }
        if (!isBlank(query.getAssigneeUserId())) {
            sql.append("AND t.assignee_user_id = ? ");
            params.add(query.getAssigneeUserId());
        }
        if (!isBlank(query.getTaskGroupId())) {
            sql.append("AND t.task_group_id = ? ");
            params.add(query.getTaskGroupId());
        }
        if (!isBlank(query.getBranchKey())) {
            sql.append("AND t.branch_key = ? ");
            params.add(query.getBranchKey());
        }
        if (query.getDueFrom() != null) {
            sql.append("AND t.due_at >= ? ");
            params.add(DefinitionRowMappers.toDbString(query.getDueFrom()));
        }
        if (query.getDueTo() != null) {
            sql.append("AND t.due_at <= ? ");
            params.add(DefinitionRowMappers.toDbString(query.getDueTo()));
        }
        if (query.getCreatedFrom() != null) {
            sql.append("AND t.created_at >= ? ");
            params.add(DefinitionRowMappers.toDbString(query.getCreatedFrom()));
        }
        if (query.getCreatedTo() != null) {
            sql.append("AND t.created_at <= ? ");
            params.add(DefinitionRowMappers.toDbString(query.getCreatedTo()));
        }
    }

    private void appendTodoFilters(StringBuilder sql, List<Object> params, TodoTaskQuery query,
                                   String currentUserId) {
        if (!isBlank(query.getProcessCode())) {
            sql.append("AND process_code = ? ");
            params.add(query.getProcessCode());
        }
        if (!isBlank(query.getProcessName())) {
            sql.append("AND LOWER(process_name) LIKE ? ");
            params.add(like(query.getProcessName()));
        }
        if (!isBlank(query.getInstanceTitle())) {
            sql.append("AND LOWER(instance_title) LIKE ? ");
            params.add(like(query.getInstanceTitle()));
        }
        if (!isBlank(query.getStarterUserId())) {
            sql.append("AND starter_user_id = ? ");
            params.add(query.getStarterUserId());
        }
        if (!isBlank(query.getNodeCode())) {
            sql.append("AND node_code = ? ");
            params.add(query.getNodeCode());
        }
        if (!isBlank(query.getTaskStatus())) {
            sql.append("AND task_status = ? ");
            params.add(query.getTaskStatus());
        }
        if ("OWN".equalsIgnoreCase(query.getTodoSource())) {
            sql.append("AND delegate_from_user_id IS NULL ");
        } else if ("DELEGATED".equalsIgnoreCase(query.getTodoSource())) {
            sql.append("AND delegate_from_user_id IS NOT NULL AND assignee_user_id = ? ");
            params.add(currentUserId);
        }
        if (query.getCreatedFrom() != null) {
            sql.append("AND created_at >= ? ");
            params.add(DefinitionRowMappers.toDbString(query.getCreatedFrom()));
        }
        if (query.getCreatedTo() != null) {
            sql.append("AND created_at <= ? ");
            params.add(DefinitionRowMappers.toDbString(query.getCreatedTo()));
        }
    }

    private void appendTodoOrder(StringBuilder sql, TodoTaskQuery query) {
        String direction = "ASC".equalsIgnoreCase(query.getSortDirection()) ? "ASC" : "DESC";
        if ("dueAt".equalsIgnoreCase(query.getSortBy())) {
            sql.append("ORDER BY CASE WHEN due_at IS NULL THEN 1 ELSE 0 END ASC, due_at ")
                    .append(direction).append(", created_at DESC, task_id DESC");
            return;
        }
        sql.append("ORDER BY created_at ").append(direction).append(", task_id DESC");
    }

    private String like(String value) {
        return "%" + value.trim().toLowerCase() + "%";
    }

    private String emptyToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

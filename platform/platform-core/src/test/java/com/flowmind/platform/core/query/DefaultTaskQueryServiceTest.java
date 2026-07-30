package com.flowmind.platform.core.query;

import com.flowmind.platform.api.dto.CompletedTaskQuery;
import com.flowmind.platform.api.dto.DelegateRelationDTO;
import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessCommentDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.StartedInstanceQuery;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.TodoTaskQuery;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.core.task.HistoryArchiveCommand;
import com.flowmind.platform.core.task.HistoryTaskWriter;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessHistoryTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import com.flowmind.platform.testsupport.ExistingConnectionDataSource;
import com.flowmind.platform.testsupport.SchemaTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultTaskQueryServiceTest {

    private Connection connection;
    private JdbcTemplate jdbcTemplate;
    private ProcessHistoryTaskRepository historyTaskRepository;
    private ActiveTaskRepository activeTaskRepository;
    private ProcessInstanceRepository instanceRepository;
    private HistoryTaskWriter historyTaskWriter;
    private DefaultTaskQueryService taskQueryService;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        SchemaTestSupport.executeSchema(connection);
        jdbcTemplate = new JdbcTemplate(new ExistingConnectionDataSource(connection));
        historyTaskRepository = new ProcessHistoryTaskRepository(jdbcTemplate);
        activeTaskRepository = new ActiveTaskRepository(jdbcTemplate);
        instanceRepository = new ProcessInstanceRepository(jdbcTemplate);
        historyTaskWriter = new HistoryTaskWriter(historyTaskRepository);
        taskQueryService = new DefaultTaskQueryService(historyTaskRepository, activeTaskRepository,
                instanceRepository, new ProcessTraceAssembler(), new RuntimeQueryAssembler(),
                () -> new UserContext("operator-001", "Operator", "dept-001", "Dept"),
                (delegateUserId, at) -> Arrays.asList(new DelegateRelationDTO("principal-001",
                        "Principal", "operator-001", "Operator", at.minusDays(1), at.plusDays(1))));
        insertDefinitionAndInstance();
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void historyWriterArchivesTaskAndQueryServiceReturnsTraceAndComments() {
        ProcessHistoryTaskEntity first = historyTaskWriter.archive(command(
                "task-apply", "apply", "op-apply", "提交申请", LocalDateTime.of(2026, 7, 22, 9, 0)));
        ProcessHistoryTaskEntity second = historyTaskWriter.archive(command(
                "task-review", "review", "op-review", "同意", LocalDateTime.of(2026, 7, 22, 10, 0)));
        historyTaskWriter.archive(command(
                "task-finance", "finance", "op-finance", " ", LocalDateTime.of(2026, 7, 22, 11, 0)));

        List<HistoryTaskDTO> history = taskQueryService.queryHistoryTasks("instance-1");
        List<ProcessCommentDTO> comments = taskQueryService.queryComments("instance-1");

        assertEquals(3, history.size());
        assertEquals(first.getId(), history.get(0).getHistoryTaskId());
        assertEquals(second.getId(), history.get(1).getHistoryTaskId());
        assertEquals("提交申请", history.get(0).getComment());
        assertEquals("1000", String.valueOf(history.get(0).getVariablesSnapshot().get("amount")));
        assertEquals(2, comments.size());
        assertEquals(first.getId(), comments.get(0).getCommentId());
        assertEquals("task-apply", comments.get(0).getTaskId());
        assertEquals("同意", comments.get(1).getComment());
    }

    @Test
    void historyWriterReturnsExistingRecordForSameTaskActionOperation() {
        HistoryArchiveCommand command = command("task-apply", "apply", "op-apply",
                "提交申请", LocalDateTime.of(2026, 7, 22, 9, 0));

        ProcessHistoryTaskEntity first = historyTaskWriter.archive(command);
        ProcessHistoryTaskEntity replay = historyTaskWriter.archive(command);

        assertEquals(first.getId(), replay.getId());
        assertEquals(1, taskQueryService.queryHistoryTasks("instance-1").size());
    }

    @Test
    void todoQueryMergesDirectCandidateAndDelegateTasksWithPreciseCandidateMatch() {
        insertTask("task-direct", "operator-001", "Operator", null, "ACTIVE", "[\"other\"]",
                LocalDateTime.of(2026, 7, 22, 9, 0));
        insertTask("task-candidate", null, null, null, "ACTIVE", "[\"operator-001\",\"other\"]",
                LocalDateTime.of(2026, 7, 22, 9, 1));
        insertTask("task-delegate", "principal-001", "Principal", null, "ACTIVE", null,
                LocalDateTime.of(2026, 7, 22, 9, 2));
        insertTask("task-u10", null, null, null, "ACTIVE", "[\"operator-0010\"]",
                LocalDateTime.of(2026, 7, 22, 9, 3));
        insertTask("task-completed", "operator-001", "Operator", null, "COMPLETED", null,
                LocalDateTime.of(2026, 7, 22, 9, 4));

        PageResult<TaskDTO> result = taskQueryService.queryTodoTasks(new TodoTaskQuery());

        assertEquals(Long.valueOf(3L), result.getTotal());
        assertEquals(3, result.getRecords().size());
        assertEquals("task-delegate", result.getRecords().get(0).getTaskId());
        assertEquals("principal-001", result.getRecords().get(0).getDelegateFromUserId());
        assertEquals("task-candidate", result.getRecords().get(1).getTaskId());
        assertEquals("task-direct", result.getRecords().get(2).getTaskId());
        assertEquals(Long.valueOf(0L), result.getRecords().get(0).getTaskVersion());
        assertEquals("review", result.getRecords().get(0).getNodeCode());
        assertEquals("Review", result.getRecords().get(0).getNodeName());
    }

    @Test
    void todoQueryHidesOpenOrSignSiblingsAfterOneSiblingIsClaimed() {
        insertTaskGroup("group-or", "OR_SIGN", 2, 0, "{}", 0L);
        insertTask("task-claimed-by-manager", "manager-001", "Manager One", null, "CLAIMED",
                "[\"manager-001\"]", LocalDateTime.of(2026, 7, 22, 9, 0), "group-or");
        insertTask("task-candidate-for-current-user", null, null, null, "ACTIVE",
                "[\"operator-001\"]", LocalDateTime.of(2026, 7, 22, 9, 1), "group-or");
        insertTask("task-normal-candidate", null, null, null, "ACTIVE",
                "[\"operator-001\"]", LocalDateTime.of(2026, 7, 22, 9, 2));

        PageResult<TaskDTO> result = taskQueryService.queryTodoTasks(new TodoTaskQuery());

        assertEquals(Long.valueOf(1L), result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("task-normal-candidate", result.getRecords().get(0).getTaskId());
    }

    @Test
    void todoQueryKeepsCountersignSiblingVisibleAfterAnotherSiblingIsClaimed() {
        insertTaskGroup("group-counter", "COUNTERSIGN", 2, 0, "{}", 0L);
        insertTask("task-claimed-by-finance-one", "finance-001", "Finance One", null, "CLAIMED",
                "[\"finance-001\"]", LocalDateTime.of(2026, 7, 22, 9, 0), "group-counter");
        insertTask("task-candidate-for-current-user", null, null, null, "ACTIVE",
                "[\"operator-001\"]", LocalDateTime.of(2026, 7, 22, 9, 1), "group-counter");

        PageResult<TaskDTO> result = taskQueryService.queryTodoTasks(new TodoTaskQuery());

        assertEquals(Long.valueOf(1L), result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("task-candidate-for-current-user", result.getRecords().get(0).getTaskId());
        assertEquals("COUNTERSIGN", jdbcTemplate.queryForObject("SELECT group_type FROM process_task_group WHERE id = ?",
                String.class, "group-counter"));
    }

    @Test
    void startedAndActiveTaskQueriesUseCurrentUserAndStableFilters() {
        insertTask("task-active", null, null, null, "ACTIVE", "[\"operator-001\"]",
                LocalDateTime.of(2026, 7, 22, 9, 0));
        insertOtherInstance();

        StartedInstanceQuery query = new StartedInstanceQuery();
        query.setInstanceStatus("RUNNING");
        query.setCurrentNodeCode("review");
        PageResult<ProcessInstanceDTO> started = taskQueryService.queryStartedInstances(query);
        List<TaskDTO> activeTasks = taskQueryService.queryActiveTasks("instance-1");

        assertEquals(Long.valueOf(1L), started.getTotal());
        assertEquals("instance-1", started.getRecords().get(0).getInstanceId());
        assertEquals(1, activeTasks.size());
        assertEquals("task-active", activeTasks.get(0).getTaskId());
        assertThrows(IllegalArgumentException.class, () -> {
            StartedInstanceQuery illegalQuery = new StartedInstanceQuery();
            illegalQuery.setStarterUserId("other-user");
            taskQueryService.queryStartedInstances(illegalQuery);
        });
    }

    @Test
    void completedQueryUsesCurrentUserAndReturnsDisplayFields() {
        historyTaskWriter.archive(command("task-apply", "apply", "op-apply",
                "提交申请", LocalDateTime.of(2026, 7, 22, 9, 0)));
        historyTaskWriter.archive(command("task-review", "review", "op-review",
                "同意", LocalDateTime.of(2026, 7, 22, 10, 0)));

        CompletedTaskQuery query = new CompletedTaskQuery();
        query.setNodeCode("review");
        PageResult<HistoryTaskDTO> result = taskQueryService.queryCompletedTasks(query);

        assertEquals(Long.valueOf(1L), result.getTotal());
        assertEquals("review", result.getRecords().get(0).getNodeCode());
        assertEquals("Review", result.getRecords().get(0).getNodeName());
        assertEquals("history-test", result.getRecords().get(0).getProcessCode());
        assertEquals("History Test Instance", result.getRecords().get(0).getInstanceTitle());
        assertThrows(IllegalArgumentException.class, () -> {
            CompletedTaskQuery illegalQuery = new CompletedTaskQuery();
            illegalQuery.setUserId("other-user");
            taskQueryService.queryCompletedTasks(illegalQuery);
        });
    }

    private HistoryArchiveCommand command(String taskId,
                                          String nodeCode,
                                          String operationId,
                                          String comment,
                                          LocalDateTime completedAt) {
        HistoryArchiveCommand command = new HistoryArchiveCommand();
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        instance.setId("instance-1");
        ProcessActiveTaskEntity task = new ProcessActiveTaskEntity();
        task.setId(taskId);
        task.setInstanceId("instance-1");
        task.setNodeCode(nodeCode);
        task.setTaskGroupId("group-1");
        task.setBranchKey("main");
        task.setCreatedAt(completedAt.minusMinutes(30));
        Map<String, Object> variables = new LinkedHashMap<String, Object>();
        variables.put("amount", Integer.valueOf(1000));
        command.setInstance(instance);
        command.setTask(task);
        command.setOperator(new UserContext("operator-001", "Operator", "dept-001", "Dept"));
        command.setActionType(ActionTypeEnum.APPROVE);
        command.setOperationId(operationId);
        command.setComment(comment);
        command.setVariablesSnapshot(variables);
        command.setCompletedAt(completedAt);
        return command;
    }

    private void insertDefinitionAndInstance() {
        jdbcTemplate.update("INSERT INTO process_definition "
                        + "(id, process_code, process_name, system_code, version, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "definition-1", "history-test", "History Test", "test", 1, "test");
        jdbcTemplate.update("INSERT INTO process_instance "
                        + "(id, definition_id, process_code, process_name, version, instance_title, "
                        + "starter_user_id, starter_user_name, current_node_codes, instance_status, started_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "instance-1", "definition-1", "history-test", "History Test", 1,
                "History Test Instance", "operator-001", "Operator", "[\"review\"]", "RUNNING",
                "2026-07-22 08:00:00");
        jdbcTemplate.update("INSERT INTO process_node "
                        + "(id, definition_id, node_code, node_name, node_type, sort_order) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "node-apply", "definition-1", "apply", "Apply", "USER_TASK", 1);
        jdbcTemplate.update("INSERT INTO process_node "
                        + "(id, definition_id, node_code, node_name, node_type, sort_order) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "node-review", "definition-1", "review", "Review", "USER_TASK", 2);
    }

    private void insertTask(String id,
                            String assigneeUserId,
                            String assigneeUserName,
                            String delegateFromUserId,
                            String status,
                            String candidates,
                            LocalDateTime createdAt) {
        insertTask(id, assigneeUserId, assigneeUserName, delegateFromUserId, status, candidates, createdAt, null);
    }

    private void insertTask(String id,
                            String assigneeUserId,
                            String assigneeUserName,
                            String delegateFromUserId,
                            String status,
                            String candidates,
                            LocalDateTime createdAt,
                            String taskGroupId) {
        jdbcTemplate.update("INSERT INTO process_active_task "
                        + "(id, instance_id, definition_id, node_code, candidate_user_ids, assignee_user_id, "
                        + "assignee_user_name, delegate_from_user_id, task_status, task_group_id, lock_version, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, "instance-1", "definition-1", "review", candidates, assigneeUserId,
                assigneeUserName, delegateFromUserId, status, taskGroupId, Long.valueOf(0L),
                createdAt.toString());
    }

    private void insertTaskGroup(String id, String type, int totalCount, int completedCount,
                                 String branchStateJson, long lockVersion) {
        jdbcTemplate.update("INSERT INTO process_task_group "
                        + "(id, instance_id, node_code, group_type, total_count, completed_count, "
                        + "branch_state_json, group_status, lock_version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)",
                id, "instance-1", "review", type, totalCount, completedCount,
                branchStateJson, Long.valueOf(lockVersion));
    }

    private void insertOtherInstance() {
        jdbcTemplate.update("INSERT INTO process_instance "
                        + "(id, definition_id, process_code, process_name, version, instance_title, "
                        + "starter_user_id, starter_user_name, current_node_codes, instance_status, started_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "instance-other", "definition-1", "history-test", "History Test", 1,
                "Other Instance", "other-user", "Other", "[\"review\"]", "RUNNING",
                "2026-07-22 08:30:00");
    }
}

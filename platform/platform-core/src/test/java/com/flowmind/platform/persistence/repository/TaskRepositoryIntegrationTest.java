package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.testsupport.ExistingConnectionDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskRepositoryIntegrationTest {

    private static final String SCHEMA = "/schema/sqlite/001_init_flow_platform.sql";

    private Connection connection;
    private JdbcTemplate jdbcTemplate;
    private ActiveTaskRepository activeTaskRepository;
    private TaskGroupRepository taskGroupRepository;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        executeSchema(connection);
        ExistingConnectionDataSource dataSource = new ExistingConnectionDataSource(connection);
        jdbcTemplate = new JdbcTemplate(dataSource);
        activeTaskRepository = new ActiveTaskRepository(jdbcTemplate);
        taskGroupRepository = new TaskGroupRepository(jdbcTemplate);
        insertDefinitionAndInstance();
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void sameTaskVersionCanOnlyBeCompletedOnce() {
        insertTask("task-complete", "ACTIVE", 0L);

        int firstRequest = activeTaskRepository.complete("task-complete", 0L);
        int secondRequest = activeTaskRepository.complete("task-complete", 0L);

        assertEquals(1, firstRequest);
        assertEquals(0, secondRequest);
        assertTask("task-complete", "COMPLETED", 1L, null);
    }

    @Test
    void activeTaskUpdatesRequireAllowedStateAndExpectedVersion() {
        insertTask("task-cancel", "ACTIVE", 0L);
        insertTask("task-claim", "ACTIVE", 0L);
        insertTask("task-transfer", "ACTIVE", 0L);
        insertTask("task-terminal", "COMPLETED", 3L);

        assertEquals(1, activeTaskRepository.cancel("task-cancel", 0L));
        assertEquals(1, activeTaskRepository.claim("task-claim", 0L, "user-a", "User A"));
        assertEquals(0, activeTaskRepository.claim("task-claim", 0L, "user-b", "User B"));
        assertEquals(1, activeTaskRepository.unclaim("task-claim", 1L));
        assertEquals(1, activeTaskRepository.transfer("task-transfer", 0L, "user-c", "User C"));

        assertEquals(0, activeTaskRepository.cancel("task-transfer", 0L));
        assertEquals(0, activeTaskRepository.transfer("task-terminal", 3L, "user-d", "User D"));
        assertTask("task-cancel", "CANCELED", 1L, null);
        assertTask("task-claim", "ACTIVE", 2L, null);
        assertTask("task-transfer", "ACTIVE", 1L, "user-c");
    }

    @Test
    void countersignCountNeverExceedsTotalCount() {
        insertTaskGroup("group-counter", "COUNTERSIGN", 2, 0, "{}", 0L);

        assertEquals(1, taskGroupRepository.incrementCompletedCount("group-counter", 0L));
        assertEquals(0, taskGroupRepository.incrementCompletedCount("group-counter", 0L));
        assertEquals(1, taskGroupRepository.incrementCompletedCount("group-counter", 1L));
        assertEquals(0, taskGroupRepository.incrementCompletedCount("group-counter", 2L));

        assertGroup("group-counter", 2, "COMPLETED", 2L);
    }

    @Test
    void parallelBranchArrivalIsDeduplicatedInsideConditionalUpdate() {
        insertTaskGroup("group-parallel", "PARALLEL_GATEWAY", 2, 0,
                "{\"branch-a\":\"RUNNING\",\"branch-b\":\"RUNNING\"}", 0L);

        assertEquals(1, taskGroupRepository.markBranchArrived("group-parallel", "branch-a", 0L));
        assertEquals(0, taskGroupRepository.markBranchArrived("group-parallel", "branch-a", 1L));
        assertEquals(1, taskGroupRepository.markBranchArrived("group-parallel", "branch-b", 1L));

        assertGroup("group-parallel", 2, "COMPLETED", 2L);
        String branchState = taskGroupRepository.findBranchStateJson("group-parallel");
        assertTrue(branchState.contains("\"branch-a\":\"ARRIVED\""));
        assertTrue(branchState.contains("\"branch-b\":\"ARRIVED\""));
    }

    @Test
    void taskGroupStatusUpdatesRequireActiveStateAndExpectedVersion() {
        insertTaskGroup("group-complete", "OR_SIGN", 1, 0, "{}", 0L);
        insertTaskGroup("group-cancel", "OR_SIGN", 1, 0, "{}", 0L);

        assertEquals(1, taskGroupRepository.complete("group-complete", 0L));
        assertEquals(0, taskGroupRepository.complete("group-complete", 0L));
        assertEquals(1, taskGroupRepository.cancel("group-cancel", 0L));
        assertEquals(0, taskGroupRepository.incrementCompletedCount("group-cancel", 1L));
    }

    @Test
    void conflictCodeContractIsStable() {
        assertEquals("FLOW_TASK_CONCURRENT_MODIFIED",
                RepositoryConflictCodes.TASK_CONCURRENT_MODIFIED);
        assertEquals("FLOW_TASK_GROUP_CONCURRENT_MODIFIED",
                RepositoryConflictCodes.TASK_GROUP_CONCURRENT_MODIFIED);
    }

    private void insertDefinitionAndInstance() {
        jdbcTemplate.update("INSERT INTO process_definition "
                        + "(id, process_code, process_name, system_code, version, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "definition-1", "repository-test", "Repository Test", "test", 1, "test");
        jdbcTemplate.update("INSERT INTO process_instance "
                        + "(id, definition_id, process_code, process_name, version, instance_title, "
                        + "starter_user_id, starter_user_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "instance-1", "definition-1", "repository-test", "Repository Test", 1,
                "Repository Test Instance", "starter", "Starter");
    }

    private void insertTask(String id, String status, long lockVersion) {
        jdbcTemplate.update("INSERT INTO process_active_task "
                        + "(id, instance_id, definition_id, node_code, task_status, lock_version) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, "instance-1", "definition-1", "review", status, lockVersion);
    }

    private void insertTaskGroup(String id, String type, int totalCount, int completedCount,
                                 String branchStateJson, long lockVersion) {
        jdbcTemplate.update("INSERT INTO process_task_group "
                        + "(id, instance_id, node_code, group_type, total_count, completed_count, "
                        + "branch_state_json, group_status, lock_version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)",
                id, "instance-1", "review", type, totalCount, completedCount,
                branchStateJson, lockVersion);
    }

    private void assertTask(String id, String status, long lockVersion, String assigneeUserId) {
        jdbcTemplate.queryForObject("SELECT task_status, lock_version, assignee_user_id "
                        + "FROM process_active_task WHERE id = ?",
                (resultSet, rowNum) -> {
                    assertEquals(status, resultSet.getString("task_status"));
                    assertEquals(lockVersion, resultSet.getLong("lock_version"));
                    assertEquals(assigneeUserId, resultSet.getString("assignee_user_id"));
                    return null;
                }, id);
    }

    private void assertGroup(String id, int completedCount, String status, long lockVersion) {
        jdbcTemplate.queryForObject("SELECT completed_count, group_status, lock_version "
                        + "FROM process_task_group WHERE id = ?",
                (resultSet, rowNum) -> {
                    assertEquals(completedCount, resultSet.getInt("completed_count"));
                    assertEquals(status, resultSet.getString("group_status"));
                    assertEquals(lockVersion, resultSet.getLong("lock_version"));
                    return null;
                }, id);
    }

    private static void executeSchema(Connection connection) throws IOException, SQLException {
        String sql;
        try (InputStream input = TaskRepositoryIntegrationTest.class.getResourceAsStream(SCHEMA)) {
            if (input == null) {
                throw new IOException("Schema resource not found: " + SCHEMA);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
            sql = new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
        try (Statement statement = connection.createStatement()) {
            for (String command : sql.split(";")) {
                if (!command.trim().isEmpty()) {
                    statement.execute(command);
                }
            }
        }
    }
}

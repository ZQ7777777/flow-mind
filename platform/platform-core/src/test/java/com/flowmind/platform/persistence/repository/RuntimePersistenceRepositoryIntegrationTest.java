package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.entity.ProcessTaskGroupEntity;
import com.flowmind.platform.testsupport.ExistingConnectionDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimePersistenceRepositoryIntegrationTest {

    private static final String SCHEMA = "/schema/sqlite/001_init_flow_platform.sql";

    private Connection connection;
    private JdbcTemplate jdbcTemplate;
    private ProcessDefinitionRepository definitionRepository;
    private ProcessInstanceRepository instanceRepository;
    private ActiveTaskRepository activeTaskRepository;
    private HistoryTaskRepository historyTaskRepository;
    private TaskGroupRepository taskGroupRepository;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        executeSchema(connection);
        jdbcTemplate = new JdbcTemplate(new ExistingConnectionDataSource(connection));
        definitionRepository = new ProcessDefinitionRepository(jdbcTemplate);
        instanceRepository = new ProcessInstanceRepository(jdbcTemplate);
        activeTaskRepository = new ActiveTaskRepository(jdbcTemplate);
        historyTaskRepository = new HistoryTaskRepository(jdbcTemplate);
        taskGroupRepository = new TaskGroupRepository(jdbcTemplate);
        insertDefinition("definition-active", "PUBLISHED", "ACTIVE", "OFF");
        insertInstance("instance-running", "RUNNING");
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void newStartOnlyFindsPublishedActiveAndFullDefinition() {
        insertDefinition("definition-draft", "DRAFT", "ACTIVE", "OFF");
        insertDefinition("definition-inactive", "PUBLISHED", "INACTIVE", "OFF");
        insertDefinition("definition-gray", "PUBLISHED", "ACTIVE", "ON");

        assertEquals("definition-active",
                definitionRepository.findActiveFullByProcessCode("runtime-test").getId());
        assertNull(definitionRepository.findActiveFullByProcessCode("unknown"));
    }

    @Test
    void instanceCanUpdateRuntimeFieldsOnlyBeforeCompletion() {
        assertEquals(1, instanceRepository.updateVariablesJson("instance-running", "{\"amount\":100}"));
        assertEquals(1, instanceRepository.updateCurrentNodeCodes("instance-running", "[\"review\"]"));
        assertEquals(1, instanceRepository.complete("instance-running",
                LocalDateTime.of(2026, 7, 22, 10, 30)));
        assertEquals(0, instanceRepository.updateVariablesJson("instance-running", "{\"amount\":200}"));
        assertEquals(0, instanceRepository.updateCurrentNodeCodes("instance-running", "[\"done\"]"));
        assertEquals(0, instanceRepository.complete("instance-running", LocalDateTime.now()));

        ProcessInstanceEntity entity = instanceRepository.findById("instance-running");
        assertEquals("COMPLETED", entity.getInstanceStatus());
        assertEquals("{\"amount\":100}", entity.getVariablesJson());
        assertEquals("[\"review\"]", entity.getCurrentNodeCodes());
        assertEquals(LocalDateTime.of(2026, 7, 22, 10, 30), entity.getEndedAt());
    }

    @Test
    void openTasksAndGroupsUseStatusFiltersAndStableOrdering() {
        activeTaskRepository.insert(activeTask("task-later", "ACTIVE", LocalDateTime.of(2026, 7, 22, 9, 1)));
        activeTaskRepository.insert(activeTask("task-earlier", "CLAIMED", LocalDateTime.of(2026, 7, 22, 9, 0)));
        activeTaskRepository.insert(activeTask("task-completed", "COMPLETED", LocalDateTime.of(2026, 7, 22, 9, 2)));
        taskGroupRepository.insert(taskGroup("group-active", "ACTIVE"));
        taskGroupRepository.insert(taskGroup("group-completed", "COMPLETED"));

        List<ProcessActiveTaskEntity> openTasks = activeTaskRepository.findOpenByInstanceId("instance-running");
        assertEquals(2, openTasks.size());
        assertEquals("task-earlier", openTasks.get(0).getId());
        assertEquals("task-later", openTasks.get(1).getId());
        assertEquals(2L, activeTaskRepository.countOpenByInstanceId("instance-running"));
        assertEquals("task-later", activeTaskRepository.findById("task-later").getId());
        assertEquals(1L, taskGroupRepository.countActiveByInstanceId("instance-running"));
        assertEquals("group-active", taskGroupRepository.findById("group-active").getId());
    }

    @Test
    void historyTasksRequireOperationIdKeepStableOrderingAndRespectUniqueConstraint() {
        assertThrows(IllegalArgumentException.class,
                () -> historyTaskRepository.insert(historyTask("history-invalid", "", "task-1",
                        LocalDateTime.of(2026, 7, 22, 9, 0))));
        historyTaskRepository.insert(historyTask("history-later", "operation-later", "task-1",
                LocalDateTime.of(2026, 7, 22, 9, 1)));
        historyTaskRepository.insert(historyTask("history-earlier", "operation-earlier", "task-2",
                LocalDateTime.of(2026, 7, 22, 9, 0)));

        List<ProcessHistoryTaskEntity> history = historyTaskRepository.findByInstanceId("instance-running");
        assertEquals(2, history.size());
        assertEquals("history-earlier", history.get(0).getId());
        assertEquals("history-later", history.get(1).getId());
        assertThrows(DataAccessException.class,
                () -> historyTaskRepository.insert(historyTask("history-duplicate", "operation-later", "task-1",
                        LocalDateTime.of(2026, 7, 22, 9, 2))));
    }

    private void insertDefinition(String id, String definitionStatus, String activationStatus, String grayStatus) {
        jdbcTemplate.update("INSERT INTO process_definition "
                        + "(id, process_code, process_name, system_code, version, definition_status, "
                        + "activation_status, gray_status, gray_rule_config, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, "runtime-test", "Runtime Test", "test", versionOf(id), definitionStatus,
                activationStatus, grayStatus, "ON".equals(grayStatus) ? "{}" : null, "test");
    }

    private int versionOf(String id) {
        if ("definition-active".equals(id)) {
            return 1;
        }
        if ("definition-draft".equals(id)) {
            return 2;
        }
        if ("definition-inactive".equals(id)) {
            return 3;
        }
        return 4;
    }

    private void insertInstance(String id, String status) {
        ProcessInstanceEntity entity = new ProcessInstanceEntity();
        entity.setId(id);
        entity.setDefinitionId("definition-active");
        entity.setProcessCode("runtime-test");
        entity.setProcessName("Runtime Test");
        entity.setVersion(Integer.valueOf(1));
        entity.setInstanceTitle("Runtime Test Instance");
        entity.setStarterUserId("starter");
        entity.setStarterUserName("Starter");
        entity.setInstanceStatus(status);
        instanceRepository.insert(entity);
    }

    private ProcessActiveTaskEntity activeTask(String id, String status, LocalDateTime createdAt) {
        ProcessActiveTaskEntity entity = new ProcessActiveTaskEntity();
        entity.setId(id);
        entity.setInstanceId("instance-running");
        entity.setDefinitionId("definition-active");
        entity.setNodeCode("review");
        entity.setCandidateUserIds("[\"user-a\"]");
        entity.setTaskStatus(status);
        entity.setLockVersion(Long.valueOf(0));
        entity.setCreatedAt(createdAt);
        return entity;
    }

    private ProcessTaskGroupEntity taskGroup(String id, String status) {
        ProcessTaskGroupEntity entity = new ProcessTaskGroupEntity();
        entity.setId(id);
        entity.setInstanceId("instance-running");
        entity.setNodeCode("parallel-split");
        entity.setGroupType("PARALLEL_GATEWAY");
        entity.setTotalCount(Integer.valueOf(2));
        entity.setCompletedCount(Integer.valueOf(0));
        entity.setBranchStateJson("{\"branch-a\":\"RUNNING\",\"branch-b\":\"RUNNING\"}");
        entity.setGroupStatus(status);
        entity.setLockVersion(Long.valueOf(0));
        return entity;
    }

    private ProcessHistoryTaskEntity historyTask(String id, String operationId, String activeTaskId,
                                                  LocalDateTime completedAt) {
        ProcessHistoryTaskEntity entity = new ProcessHistoryTaskEntity();
        entity.setId(id);
        entity.setInstanceId("instance-running");
        entity.setOperationId(operationId);
        entity.setActiveTaskId(activeTaskId);
        entity.setNodeCode("review");
        entity.setHandleType("NORMAL");
        entity.setActionType("APPROVE");
        entity.setVariablesSnapshot("{\"amount\":100}");
        entity.setCompletedAt(completedAt);
        return entity;
    }

    private static void executeSchema(Connection connection) throws IOException, SQLException {
        String sql;
        try (InputStream input = RuntimePersistenceRepositoryIntegrationTest.class.getResourceAsStream(SCHEMA)) {
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

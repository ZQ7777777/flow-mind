package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import com.flowmind.platform.persistence.repository.TaskGroupRepository;
import com.flowmind.platform.testsupport.ExistingConnectionDataSource;
import com.flowmind.platform.testsupport.SchemaTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeStateValidatorTest {

    private Connection connection;
    private JdbcTemplate jdbcTemplate;
    private RuntimeStateValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        SchemaTestSupport.executeSchema(connection);
        jdbcTemplate = new JdbcTemplate(new ExistingConnectionDataSource(connection));
        ActiveTaskRepository activeTaskRepository = new ActiveTaskRepository(jdbcTemplate);
        ProcessInstanceRepository processInstanceRepository = new ProcessInstanceRepository(jdbcTemplate);
        TaskGroupRepository taskGroupRepository = new TaskGroupRepository(jdbcTemplate);
        validator = new RuntimeStateValidator(activeTaskRepository, processInstanceRepository, taskGroupRepository);
        insertDefinitionAndInstance("RUNNING");
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void validateTaskActionReturnsTaskAndInstanceContext() {
        insertTask("task-001", "ACTIVE", 0L);

        RuntimeTaskContext context = validator.validateTaskAction("task-001", Long.valueOf(0),
                ActionTypeEnum.APPROVE, operator());

        assertEquals("instance-1", context.getInstance().getId());
        assertEquals("task-001", context.getTask().getId());
        assertEquals("operator-001", context.getOperator().getUserId());
    }

    @Test
    void validateTaskActionRejectsInvalidTaskStatusAndVersion() {
        insertTask("task-completed", "COMPLETED", 1L);
        insertTask("task-active", "ACTIVE", 1L);

        RuntimeValidationException status = assertThrows(RuntimeValidationException.class,
                () -> validator.validateTaskAction("task-completed", Long.valueOf(1),
                        ActionTypeEnum.APPROVE, operator()));
        RuntimeValidationException version = assertThrows(RuntimeValidationException.class,
                () -> validator.validateTaskAction("task-active", Long.valueOf(0),
                        ActionTypeEnum.APPROVE, operator()));

        assertEquals(RuntimeErrorCodes.TASK_STATUS_INVALID, status.getErrorCode());
        assertEquals(RuntimeErrorCodes.TASK_CONCURRENT_MODIFIED, version.getErrorCode());
    }

    @Test
    void validateTaskActionRejectsNonRunningInstance() {
        jdbcTemplate.update("UPDATE process_instance SET instance_status = 'COMPLETED' WHERE id = 'instance-1'");
        insertTask("task-001", "ACTIVE", 0L);

        RuntimeValidationException ex = assertThrows(RuntimeValidationException.class,
                () -> validator.validateTaskAction("task-001", Long.valueOf(0),
                        ActionTypeEnum.APPROVE, operator()));

        assertEquals(RuntimeErrorCodes.INSTANCE_STATUS_INVALID, ex.getErrorCode());
    }

    @Test
    void validateTaskGroupReadsBranchStateJsonAndVersion() {
        jdbcTemplate.update("INSERT INTO process_task_group "
                        + "(id, instance_id, node_code, group_type, total_count, completed_count, "
                        + "branch_state_json, group_status, lock_version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "group-001", "instance-1", "gateway", "PARALLEL_GATEWAY", 2, 0,
                "{\"branch-a\":\"RUNNING\"}", "ACTIVE", 3);

        assertEquals("{\"branch-a\":\"RUNNING\"}", validator.validateTaskGroupState(
                "group-001", Long.valueOf(3), ActionTypeEnum.APPROVE).getBranchStateJson());
    }

    private void insertDefinitionAndInstance(String status) {
        jdbcTemplate.update("INSERT INTO process_definition "
                        + "(id, process_code, process_name, system_code, version, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "definition-1", "runtime-test", "Runtime Test", "test", 1, "test");
        jdbcTemplate.update("INSERT INTO process_instance "
                        + "(id, definition_id, process_code, process_name, version, instance_title, "
                        + "starter_user_id, starter_user_name, instance_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "instance-1", "definition-1", "runtime-test", "Runtime Test", 1,
                "Runtime Test Instance", "starter", "Starter", status);
    }

    private void insertTask(String id, String status, long lockVersion) {
        jdbcTemplate.update("INSERT INTO process_active_task "
                        + "(id, instance_id, definition_id, node_code, task_status, lock_version) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, "instance-1", "definition-1", "review", status, lockVersion);
    }

    private UserContext operator() {
        return new UserContext("operator-001", "Operator", "dept-001", "Dept");
    }
}

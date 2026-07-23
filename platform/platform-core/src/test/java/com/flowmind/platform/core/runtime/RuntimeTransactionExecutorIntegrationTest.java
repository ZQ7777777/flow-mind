package com.flowmind.platform.core.runtime;

import com.flowmind.platform.testsupport.ExistingConnectionDataSource;
import com.flowmind.platform.testsupport.SchemaTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 验证第五步主事务会一并回滚运行时数据和 Outbox。 */
class RuntimeTransactionExecutorIntegrationTest {

    private Connection connection;
    private JdbcTemplate jdbcTemplate;
    private RuntimeTransactionExecutor transactionExecutor;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        SchemaTestSupport.executeSchema(connection);
        ExistingConnectionDataSource dataSource = new ExistingConnectionDataSource(connection);
        jdbcTemplate = new JdbcTemplate(dataSource);
        transactionExecutor = new RuntimeTransactionExecutor(new DataSourceTransactionManager(dataSource));
        jdbcTemplate.update("INSERT INTO process_definition "
                        + "(id, process_code, process_name, system_code, version, created_by) VALUES (?, ?, ?, ?, ?, ?)",
                "definition-1", "transaction-test", "Transaction Test", "test", 1, "tester");
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void rollsBackInstanceTaskHistoryAndOutboxWhenCallbackStageFails() {
        assertThrows(IllegalStateException.class, () -> transactionExecutor.execute(new RuntimeTransactionWork<Void>() {
            @Override
            public Void execute() {
                jdbcTemplate.update("INSERT INTO process_instance "
                                + "(id, definition_id, process_code, process_name, version, instance_title, "
                                + "starter_user_id, starter_user_name, instance_status) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        "instance-1", "definition-1", "transaction-test", "Transaction Test", 1,
                        "Transaction Test", "starter", "Starter", "RUNNING");
                jdbcTemplate.update("INSERT INTO process_active_task "
                                + "(id, instance_id, definition_id, node_code, task_status) VALUES (?, ?, ?, ?, ?)",
                        "task-next", "instance-1", "definition-1", "review", "ACTIVE");
                jdbcTemplate.update("INSERT INTO process_history_task "
                                + "(id, instance_id, operation_id, active_task_id, node_code, handle_type, action_type) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                        "history-1", "instance-1", "operation-1", "task-old", "apply", "NORMAL", "SEND");
                jdbcTemplate.update("INSERT INTO process_callback_log "
                                + "(id, event_id, instance_id, operation_id, event_type, action_type, payload_json) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                        "callback-1", "operation-1:TASK_CREATED:task-next", "instance-1", "operation-1",
                        "TASK_CREATED", "SEND", "{}");
                throw new IllegalStateException("simulated outbox failure");
            }
        }));

        assertEquals(0, count("process_instance"));
        assertEquals(0, count("process_active_task"));
        assertEquals(0, count("process_history_task"));
        assertEquals(0, count("process_callback_log"));
    }

    private int count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class).intValue();
    }
}

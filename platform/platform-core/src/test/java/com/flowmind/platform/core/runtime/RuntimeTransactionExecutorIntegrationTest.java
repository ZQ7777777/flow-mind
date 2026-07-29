package com.flowmind.platform.core.runtime;

import com.flowmind.platform.testsupport.ExistingConnectionDataSource;
import com.flowmind.platform.testsupport.SchemaTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

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

    @Test
    void retriesSqliteBusyRuntimeTransactionAndReturnsSecondAttemptResult() {
        CountingTransactionManager retryTransactionManager = new CountingTransactionManager();
        RuntimeTransactionExecutor retryExecutor = new RuntimeTransactionExecutor(retryTransactionManager);
        final int[] workAttempts = {0};

        String result = retryExecutor.execute(new RuntimeTransactionWork<String>() {
            @Override
            public String execute() {
                workAttempts[0] += 1;
                if (workAttempts[0] == 1) {
                    throw new DataAccessResourceFailureException("[SQLITE_BUSY] database is locked");
                }
                return "started";
            }
        });

        assertEquals("started", result);
        assertEquals(2, workAttempts[0]);
        assertEquals(2, retryTransactionManager.beginCount);
        assertEquals(1, retryTransactionManager.rollbackCount);
        assertEquals(1, retryTransactionManager.commitCount);
    }

    private int count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class).intValue();
    }

    private static final class CountingTransactionManager implements PlatformTransactionManager {
        private int beginCount;
        private int commitCount;
        private int rollbackCount;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            beginCount += 1;
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            commitCount += 1;
        }

        @Override
        public void rollback(TransactionStatus status) {
            rollbackCount += 1;
        }
    }
}

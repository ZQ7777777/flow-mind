package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.testsupport.ExistingConnectionDataSource;
import com.flowmind.platform.testsupport.SchemaTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 实例物理删除关联数据与可追溯日志保留的集成测试。 */
class ProcessInstanceDeletionRepositoryIntegrationTest {

    private Connection connection;
    private JdbcTemplate jdbcTemplate;
    private ProcessInstanceDeletionRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        SchemaTestSupport.executeSchema(connection);
        jdbcTemplate = new JdbcTemplate(new ExistingConnectionDataSource(connection));
        repository = new ProcessInstanceDeletionRepository(jdbcTemplate);
        insertRuntimeData();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void deletesRuntimeRowsKeepsOperationRecordAndAnonymizesLogReferences() {
        assertEquals(1, repository.deleteRuntimeData("instance-1"));

        assertEquals(0, count("process_instance"));
        assertEquals(0, count("process_task_group"));
        assertEquals(0, count("process_active_task"));
        assertEquals(0, count("process_history_task"));
        assertEquals(0, count("process_read_record"));
        assertEquals(0, count("process_attachment"));
        assertEquals(0, count("process_reminder_record"));
        assertEquals(0, count("process_alert_record"));
        assertEquals(1, count("process_operation_record"));
        assertEquals(null, jdbcTemplate.queryForObject("SELECT instance_id FROM process_audit_log WHERE id = 'audit-1'",
                String.class));
        assertEquals("HARD", jdbcTemplate.queryForObject(
                "SELECT json_extract(detail_json, '$.deleteMode') FROM process_audit_log WHERE id = 'audit-1'",
                String.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT json_extract(detail_json, '$.targetDeleted') FROM process_audit_log WHERE id = 'audit-1'",
                Integer.class).intValue());
        assertEquals(null, jdbcTemplate.queryForObject(
                "SELECT instance_id FROM process_callback_log WHERE id = 'callback-1'", String.class));
        assertEquals("HARD", jdbcTemplate.queryForObject(
                "SELECT json_extract(payload_json, '$.deleteMode') FROM process_callback_log WHERE id = 'callback-1'",
                String.class));
    }

    private void insertRuntimeData() {
        jdbcTemplate.update("INSERT INTO process_definition "
                        + "(id, process_code, process_name, system_code, version, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "definition-1", "expense", "Expense", "test", Integer.valueOf(1), "tester");
        jdbcTemplate.update("INSERT INTO process_instance "
                        + "(id, definition_id, process_code, process_name, version, instance_title, starter_user_id, "
                        + "starter_user_name, current_node_codes, variables_json, instance_status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "instance-1", "definition-1", "expense", "Expense", Integer.valueOf(1), "Expense instance",
                "starter", "Starter", "[\"review\"]", "{}", "RUNNING");
        jdbcTemplate.update("INSERT INTO process_task_group "
                        + "(id, instance_id, node_code, group_type, total_count, completed_count, group_status, lock_version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "group-1", "instance-1", "review", "OR_SIGN", Integer.valueOf(1), Integer.valueOf(0), "ACTIVE",
                Long.valueOf(0L));
        jdbcTemplate.update("INSERT INTO process_active_task "
                        + "(id, instance_id, definition_id, node_code, candidate_user_ids, task_status, task_group_id, "
                        + "lock_version) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "task-1", "instance-1", "definition-1", "review", "[\"reviewer\"]", "ACTIVE", "group-1",
                Long.valueOf(0L));
        jdbcTemplate.update("INSERT INTO process_history_task "
                        + "(id, instance_id, operation_id, active_task_id, node_code, task_group_id, handle_type, action_type) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "history-1", "instance-1", "old-operation", "task-1", "review", "group-1", "NORMAL", "APPROVE");
        jdbcTemplate.update("INSERT INTO process_read_record (id, instance_id, user_id, user_name) VALUES (?, ?, ?, ?)",
                "read-1", "instance-1", "reader", "Reader");
        jdbcTemplate.update("INSERT INTO process_attachment "
                        + "(id, instance_id, owner_type, attachment_code, file_name, size_bytes, storage_key, uploaded_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "attachment-1", "instance-1", "INSTANCE", "receipt", "receipt.pdf", Long.valueOf(12L), "file-1",
                "starter");
        jdbcTemplate.update("INSERT INTO process_reminder_record "
                        + "(id, instance_id, task_id, reminder_type, target_user_ids, message, reminder_status, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "reminder-1", "instance-1", "task-1", "MANUAL", "[\"reviewer\"]", "review", "PENDING", "starter");
        jdbcTemplate.update("INSERT INTO process_alert_record "
                        + "(id, instance_id, task_id, alert_type, severity, alert_status, detail_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                "alert-1", "instance-1", "task-1", "TASK_TIMEOUT", "LOW", "OPEN", "{}");
        jdbcTemplate.update("INSERT INTO process_audit_log "
                        + "(id, instance_id, operation_id, target_type, target_id, action_type, operator_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                "audit-1", "instance-1", "old-operation", "INSTANCE", "instance-1", "START", "starter");
        jdbcTemplate.update("INSERT INTO process_callback_log "
                        + "(id, event_id, instance_id, operation_id, event_type, action_type, payload_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                "callback-1", "event-1", "instance-1", "old-operation", "PROCESS_STARTED", "START", "{}");
        jdbcTemplate.update("INSERT INTO process_operation_record "
                        + "(id, operation_id, instance_id, action_type, operator_id, request_hash, operation_status, "
                        + "processing_expires_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now'), datetime('now'))",
                "record-1", "old-operation", "instance-1", "TERMINATE", "starter", "hash", "SUCCESS");
    }

    private long count(String table) {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return value == null ? 0L : value.longValue();
    }
}

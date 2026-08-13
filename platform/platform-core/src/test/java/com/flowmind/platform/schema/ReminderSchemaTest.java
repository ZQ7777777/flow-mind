package com.flowmind.platform.schema;

import com.flowmind.platform.testsupport.SchemaTestSupport;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ReminderSchemaTest {

    @Test
    void schemaAllowsDueSoonReminderRecords() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            SchemaTestSupport.executeSchema(connection);
            insertDefinition(connection);
            insertInstance(connection);

            assertDoesNotThrow(() -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO process_reminder_record "
                            + "(id, instance_id, task_id, reminder_type, target_user_ids, message, created_by) "
                            + "VALUES ('reminder-due-soon', 'instance-001', 'task-001', 'DUE_SOON', "
                            + "'[\"approver-1\"]', 'Task due soon reminder: approve', 'system_due_soon')");
                }
            });
        }
    }

    private void insertDefinition(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO process_definition "
                    + "(id, process_code, process_name, system_code, version, created_by) "
                    + "VALUES ('definition-001', 'expense', 'Expense', 'demo', 1, 'test')");
        }
    }

    private void insertInstance(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO process_instance "
                    + "(id, definition_id, process_code, process_name, version, instance_title, "
                    + "starter_user_id, starter_user_name) VALUES ('instance-001', 'definition-001', "
                    + "'expense', 'Expense', 1, 'Expense Instance', 'starter-001', 'Starter')");
        }
    }
}
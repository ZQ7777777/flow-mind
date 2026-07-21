package com.flowmind.platform.schema;

import com.flowmind.platform.api.enums.DefinitionActionTypeEnum;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowPlatformSchemaTest {

    private static final String SCHEMA = "/schema/sqlite/001_init_flow_platform.sql";

    @Test
    void schemaInitializesAndEnforcesDefinitionConstraints() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            executeSchema(connection);

            assertDoesNotThrow(() -> insertDefinition(connection, "definition-001", "expense", 1,
                    "DRAFT", "INACTIVE", "OFF", null));
            assertThrows(SQLException.class, () -> insertDefinition(connection, "definition-002", "expense", 1,
                    "DRAFT", "INACTIVE", "OFF", null));
            assertThrows(SQLException.class, () -> insertDefinition(connection, "definition-003", "expense", 2,
                    "DRAFT", "INACTIVE", "ON", null));
        }
    }

    @Test
    void schemaEnforcesNodeAndAttachmentConfigurationConstraints() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            executeSchema(connection);
            insertDefinition(connection, "definition-001", "expense", 1,
                    "DRAFT", "INACTIVE", "OFF", null);
            insertAttachmentTemplate(connection);

            assertDoesNotThrow(() -> insertNode(connection, "node-001", "start", "START"));
            assertThrows(SQLException.class, () -> insertNode(connection, "node-002", "start", "START"));
            assertThrows(SQLException.class, () -> insertNode(connection, "node-003", "bad", "UNKNOWN"));

            assertDoesNotThrow(() -> insertAttachmentConfig(connection, "config-row-001", "group-001", false, 0));
            assertThrows(SQLException.class, () -> insertAttachmentConfig(connection, "config-row-002", "group-001", true, 0));
        }
    }

    @Test
    void deletingDefinitionWithChildRowsRequiresExplicitServiceCleanup() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            executeSchema(connection);
            insertDefinition(connection, "definition-001", "expense", 1,
                    "DRAFT", "INACTIVE", "OFF", null);
            insertNode(connection, "node-001", "start", "START");

            assertThrows(SQLException.class, () -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DELETE FROM process_definition WHERE id = 'definition-001'");
                }
            });
        }
    }

    @Test
    void activeTaskReferencesDefinitionWithoutDuplicatingItsVersion() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            executeSchema(connection);

            Set<String> columns = tableColumns(connection, "process_active_task");
            assertTrue(columns.contains("definition_id"));
            assertTrue(columns.contains("lock_version"));
            assertFalse(columns.contains("version"));
        }
    }

    @Test
    void deletingInstanceWithCallbackLogRequiresExplicitServiceCleanup() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            executeSchema(connection);
            insertDefinition(connection, "definition-001", "expense", 1,
                    "DRAFT", "INACTIVE", "OFF", null);
            insertInstance(connection);
            insertCallbackLog(connection);

            assertThrows(SQLException.class, () -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DELETE FROM process_instance WHERE id = 'instance-001'");
                }
            });

            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT instance_id FROM process_callback_log WHERE id = 'callback-001'")) {
                assertTrue(resultSet.next());
                assertEquals("instance-001", resultSet.getString("instance_id"));
                assertFalse(resultSet.next());
            }
        }
    }

    @Test
    void operationRecordSupportsRuntimeAndDefinitionNamespacedActions() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            executeSchema(connection);

            assertDoesNotThrow(() -> insertOperationRecord(connection, "record-001", "operation-001", "APPROVE"));
            for (DefinitionActionTypeEnum actionType : DefinitionActionTypeEnum.values()) {
                String suffix = actionType.name();
                assertDoesNotThrow(() -> insertOperationRecord(connection, "record-" + suffix,
                        "operation-" + suffix, actionType.getOperationActionType()));
            }
            assertThrows(SQLException.class,
                    () -> insertOperationRecord(connection, "record-003", "operation-003", "SAVE_GRAPH"));
        }
    }

    @Test
    void auditLogSupportsRuntimeAndDefinitionNamespacedActions() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            executeSchema(connection);

            assertDoesNotThrow(() -> insertAuditLog(connection, "audit-001", "TASK", "task-001", "APPROVE"));
            for (DefinitionActionTypeEnum actionType : DefinitionActionTypeEnum.values()) {
                String suffix = actionType.name();
                assertDoesNotThrow(() -> insertAuditLog(connection, "audit-" + suffix, "DEFINITION",
                        "definition-" + suffix, actionType.getOperationActionType()));
            }
            assertThrows(SQLException.class,
                    () -> insertAuditLog(connection, "audit-003", "DEFINITION", "definition-001", "DELETE"));
        }
    }

    private static void executeSchema(Connection connection) throws IOException, SQLException {
        String sql;
        try (InputStream input = FlowPlatformSchemaTest.class.getResourceAsStream(SCHEMA)) {
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

    private static Set<String> tableColumns(Connection connection, String tableName) throws SQLException {
        Set<String> columns = new LinkedHashSet<String>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("name"));
            }
        }
        return columns;
    }

    private static void insertDefinition(Connection connection, String id, String code, int version,
                                         String definitionStatus, String activationStatus,
                                         String grayStatus, String grayRuleConfig) throws SQLException {
        String sql = "INSERT INTO process_definition "
                + "(id, process_code, process_name, system_code, version, definition_status, "
                + "activation_status, gray_status, gray_rule_config, created_by) VALUES ('"
                + id + "', '" + code + "', 'Expense', 'demo', " + version + ", '"
                + definitionStatus + "', '" + activationStatus + "', '" + grayStatus + "', "
                + (grayRuleConfig == null ? "NULL" : "'" + grayRuleConfig + "'") + ", 'test')";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static void insertNode(Connection connection, String id, String nodeCode, String nodeType)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO process_node "
                    + "(id, definition_id, node_code, node_name, node_type) VALUES ('"
                    + id + "', 'definition-001', '" + nodeCode + "', 'Node', '" + nodeType + "')");
        }
    }

    private static void insertAttachmentConfig(Connection connection, String id, String groupId,
                                               boolean required, int minCount) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO process_definition_attachment_config "
                    + "(id, attachment_config_id, definition_id, attachment_template_id, attachment_code, "
                    + "required, min_count, created_by) VALUES ('" + id + "', '" + groupId
                    + "', 'definition-001', 'template-001', 'receipt', " + (required ? 1 : 0)
                    + ", " + minCount + ", 'test')");
        }
    }

    private static void insertAttachmentTemplate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO process_attachment_template "
                    + "(id, attachment_code, template_version, attachment_name, allowed_extensions, "
                    + "max_size_bytes, created_by) VALUES ('template-001', 'receipt', 1, 'Receipt', 'pdf', 1024, 'test')");
        }
    }

    private static void insertInstance(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO process_instance "
                    + "(id, definition_id, process_code, process_name, version, instance_title, "
                    + "starter_user_id, starter_user_name) VALUES ('instance-001', 'definition-001', "
                    + "'expense', 'Expense', 1, 'Expense Instance', 'starter-001', 'Starter')");
        }
    }

    private static void insertCallbackLog(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO process_callback_log "
                    + "(id, event_id, instance_id, operation_id, event_type, action_type, payload_json) "
                    + "VALUES ('callback-001', 'event-001', 'instance-001', 'operation-001', "
                    + "'PROCESS_STARTED', 'START', '{}')");
        }
    }

    private static void insertOperationRecord(Connection connection, String id, String operationId,
                                              String actionType) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO process_operation_record "
                    + "(id, operation_id, action_type, operator_id, request_hash, processing_expires_at, expires_at) "
                    + "VALUES ('" + id + "', '" + operationId + "', '" + actionType + "', "
                    + "'operator-001', 'hash-" + operationId + "', '2026-07-16 10:05:00', '2026-07-17 10:00:00')");
        }
    }

    private static void insertAuditLog(Connection connection, String id, String targetType, String targetId,
                                       String actionType) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO process_audit_log "
                    + "(id, operation_id, target_type, target_id, action_type, operator_id, detail_json) "
                    + "VALUES ('" + id + "', 'operation-" + id + "', '" + targetType + "', '" + targetId
                    + "', '" + actionType + "', 'operator-001', '{}')");
        }
    }
}

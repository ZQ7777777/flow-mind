package com.flowmind.platform.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
}

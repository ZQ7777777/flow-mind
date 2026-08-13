package com.flowmind.platform.testsupport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public final class SchemaTestSupport {

    private static final String SCHEMA = "/schema/sqlite/001_init_flow_platform.sql";
    private static final String DELEGATE_FROM_USER_NAME_MIGRATION =
            "/schema/sqlite/005_delegate_from_user_name.sql";
    private static final String DUE_SOON_REMINDER_TYPE_MIGRATION =
            "/schema/sqlite/006_due_soon_reminder_type.sql";

    private SchemaTestSupport() {
    }

    public static void executeSchema(Connection connection) throws IOException, SQLException {
        executeScript(connection, SCHEMA);
        if (!tableHasColumn(connection, "process_active_task", "delegate_from_user_name")) {
            executeScript(connection, DELEGATE_FROM_USER_NAME_MIGRATION);
        }
        if (!tableContainsValue(connection, "process_reminder_record", "DUE_SOON")) {
            executeScript(connection, DUE_SOON_REMINDER_TYPE_MIGRATION);
        }
    }

    private static void executeScript(Connection connection, String resourcePath) throws IOException, SQLException {
        String sql;
        try (InputStream input = SchemaTestSupport.class.getResourceAsStream(resourcePath)) {
            assertNotNull(input);
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

    private static boolean tableContainsValue(Connection connection, String tableName, String value)
            throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT sql FROM sqlite_master WHERE type = 'table' "
                     + "AND name = '" + tableName + "'")) {
            return resultSet.next() && resultSet.getString("sql") != null
                    && resultSet.getString("sql").contains(value);
        }
    }

    private static boolean tableHasColumn(Connection connection, String tableName, String columnName)
            throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                if (columnName.equals(resultSet.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }
}

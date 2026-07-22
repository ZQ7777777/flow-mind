package com.flowmind.platform.testsupport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public final class SchemaTestSupport {

    private static final String SCHEMA = "/schema/sqlite/001_init_flow_platform.sql";

    private SchemaTestSupport() {
    }

    public static void executeSchema(Connection connection) throws IOException, SQLException {
        String sql;
        try (InputStream input = SchemaTestSupport.class.getResourceAsStream(SCHEMA)) {
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
}

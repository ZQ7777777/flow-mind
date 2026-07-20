package com.flowmind.platform.core.definition;

import com.flowmind.platform.api.dto.ProcessFormFieldDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.core.validation.ProcessFormFieldValidator;
import com.flowmind.platform.persistence.repository.ProcessFormFieldRepository;
import com.flowmind.platform.testsupport.ExistingConnectionDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessFormFieldDefinitionManagerIntegrationTest {

    private static final String SCHEMA = "/schema/sqlite/001_init_flow_platform.sql";

    private Connection connection;
    private JdbcTemplate jdbcTemplate;
    private ProcessFormFieldDefinitionManager manager;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        executeSchema(connection);
        jdbcTemplate = new JdbcTemplate(new ExistingConnectionDataSource(connection));
        manager = new ProcessFormFieldDefinitionManager(
                new ProcessFormFieldRepository(jdbcTemplate),
                new ProcessFormFieldValidator());
        insertDefinition("definition-source", "form_field_source");
        insertDefinition("definition-target", "form_field_target");
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void validFieldsAreSavedAndReturnedInStableOrder() {
        ValidationResult result = manager.saveFormFields("definition-source", Arrays.asList(
                field("amount", "Amount", "number", "number", 20),
                field("applicant", "Applicant", "string", "input", 10)));

        assertTrue(result.isValid());
        List<ProcessFormFieldDTO> saved = manager.findFormFields("definition-source");
        assertEquals(2, saved.size());
        assertEquals("applicant", saved.get(0).getFieldCode());
        assertEquals("amount", saved.get(1).getFieldCode());
        assertEquals("definition-source", saved.get(0).getDefinitionId());
    }

    @Test
    void invalidFieldsAreNotPersisted() {
        ValidationResult result = manager.saveFormFields("definition-source", Arrays.asList(
                field("amount", "Amount", "number", "number", 10),
                field("amount", "Duplicate Amount", "number", "number", 20)));

        assertFalse(result.isValid());
        assertTrue(manager.findFormFields("definition-source").isEmpty());
    }

    @Test
    void emptyFieldsClearExistingDefinitionFields() {
        manager.saveFormFields("definition-source", Collections.singletonList(
                field("applicant", "Applicant", "string", "input", 10)));

        ValidationResult result = manager.saveFormFields("definition-source", Collections.emptyList());

        assertTrue(result.isValid());
        assertTrue(manager.findFormFields("definition-source").isEmpty());
    }

    @Test
    void fieldsCanBeCopiedAndDeleted() {
        manager.saveFormFields("definition-source", Arrays.asList(
                field("applicant", "Applicant", "string", "input", 10),
                field("amount", "Amount", "number", "number", 20)));
        List<ProcessFormFieldDTO> source = manager.findFormFields("definition-source");

        assertEquals(2, manager.copyFormFields("definition-source", "definition-target"));

        List<ProcessFormFieldDTO> copied = manager.findFormFields("definition-target");
        assertEquals(2, copied.size());
        assertNotEquals(source.get(0).getId(), copied.get(0).getId());
        assertEquals("definition-target", copied.get(0).getDefinitionId());
        assertEquals(2, manager.deleteFormFields("definition-target"));
        assertTrue(manager.findFormFields("definition-target").isEmpty());
    }

    private ProcessFormFieldDTO field(String code, String name, String type, String control, int sortOrder) {
        ProcessFormFieldDTO field = new ProcessFormFieldDTO();
        field.setFieldCode(code);
        field.setFieldName(name);
        field.setFieldType(type);
        field.setControlType(control);
        field.setRequired(Boolean.TRUE);
        field.setValidationRule("{\"required\":true}");
        field.setSortOrder(sortOrder);
        return field;
    }

    private void insertDefinition(String id, String processCode) {
        jdbcTemplate.update("INSERT INTO process_definition "
                        + "(id, process_code, process_name, system_code, version, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, processCode, "Form Field Test", "test", 1, "tester");
    }

    private static void executeSchema(Connection connection) throws IOException, SQLException {
        String sql;
        try (InputStream input = ProcessFormFieldDefinitionManagerIntegrationTest.class.getResourceAsStream(SCHEMA)) {
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

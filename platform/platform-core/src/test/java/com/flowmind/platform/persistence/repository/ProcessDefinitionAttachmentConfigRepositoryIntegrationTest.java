package com.flowmind.platform.persistence.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.core.definition.ProcessDefinitionAttachmentConfigManager;
import com.flowmind.platform.core.validation.ProcessDefinitionAttachmentConfigValidator;
import com.flowmind.platform.persistence.entity.ProcessDefinitionAttachmentConfigEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessDefinitionAttachmentConfigRepositoryIntegrationTest {

    private static final String SCHEMA = "/schema/sqlite/001_init_flow_platform.sql";

    private Connection connection;
    private JdbcTemplate jdbcTemplate;
    private ProcessDefinitionAttachmentConfigRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        executeSchema(connection);
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new ProcessDefinitionAttachmentConfigRepository(jdbcTemplate);
        insertDefinition("definition-001");
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void replaceDraftGroupRetiresPreviousDraftGroupsForSameDefinition() {
        repository.replaceDraftGroup("definition-001", "attachment-config-001",
                Collections.singletonList(config("row-001", "attachment-config-001", "bankReceipt")));

        repository.replaceDraftGroup("definition-001", "attachment-config-002",
                Collections.singletonList(config("row-002", "attachment-config-002", "bankReceipt")));

        List<ProcessDefinitionAttachmentConfigEntity> draftRows =
                repository.findByDefinitionIdAndStatus("definition-001", "DRAFT");
        List<ProcessDefinitionAttachmentConfigEntity> inactiveRows =
                repository.findByDefinitionIdAndStatus("definition-001", "INACTIVE");

        assertEquals(1, draftRows.size());
        assertEquals("attachment-config-002", draftRows.get(0).getAttachmentConfigId());
        assertEquals(1, inactiveRows.size());
        assertEquals("attachment-config-001", inactiveRows.get(0).getAttachmentConfigId());
    }

    @Test
    void activateDraftGroupChoosesLatestDraftAndRetiresOlderDraftGroups() {
        repository.insert(config("row-001", "definition-001:attachment-config:1700000001000", "bankReceipt"));
        repository.insert(config("row-002", "definition-001:attachment-config:1700000002000", "bankReceipt"));
        ProcessAttachmentTemplateRepository templateRepository = new ProcessAttachmentTemplateRepository(jdbcTemplate);
        ProcessDefinitionAttachmentConfigManager manager = new ProcessDefinitionAttachmentConfigManager(
                repository, templateRepository, new ProcessDefinitionAttachmentConfigValidator(templateRepository),
                new ObjectMapper());

        ValidationResult result = manager.activateDraftGroup("definition-001",
                Collections.singletonList(applyNode()), "operator-test");

        List<ProcessDefinitionAttachmentConfigEntity> draftRows =
                repository.findByDefinitionIdAndStatus("definition-001", "DRAFT");
        List<ProcessDefinitionAttachmentConfigEntity> activeRows =
                repository.findByDefinitionIdAndStatus("definition-001", "ACTIVE");
        List<ProcessDefinitionAttachmentConfigEntity> inactiveRows =
                repository.findByDefinitionIdAndStatus("definition-001", "INACTIVE");

        assertTrue(result.isValid());
        assertEquals(0, draftRows.size());
        assertEquals(1, activeRows.size());
        assertEquals("definition-001:attachment-config:1700000002000", activeRows.get(0).getAttachmentConfigId());
        assertEquals(1, inactiveRows.size());
        assertEquals("definition-001:attachment-config:1700000001000", inactiveRows.get(0).getAttachmentConfigId());
    }

    private ProcessDefinitionAttachmentConfigEntity config(String id, String groupId, String attachmentCode) {
        ProcessDefinitionAttachmentConfigEntity entity = new ProcessDefinitionAttachmentConfigEntity();
        entity.setId(id);
        entity.setAttachmentConfigId(groupId);
        entity.setDefinitionId("definition-001");
        entity.setAttachmentTemplateId("template-bank-receipt");
        entity.setAttachmentCode(attachmentCode);
        entity.setRequired(Boolean.TRUE);
        entity.setMinCount(Integer.valueOf(1));
        entity.setMaxCount(Integer.valueOf(5));
        entity.setApplicableNodeCodes("[\"APPLY\"]");
        entity.setSortOrder(Integer.valueOf(1));
        entity.setCreatedBy("operator-test");
        entity.setUpdatedBy("operator-test");
        return entity;
    }

    private ProcessNodeDTO applyNode() {
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setDefinitionId("definition-001");
        node.setNodeCode("APPLY");
        node.setNodeName("Apply");
        node.setNodeType(NodeTypeEnum.USER_TASK);
        return node;
    }

    private void insertDefinition(String id) {
        jdbcTemplate.update("INSERT INTO process_definition "
                        + "(id, process_code, process_name, version, system_code, definition_status, "
                        + "activation_status, gray_status, created_by, updated_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, "deposit", "Deposit", Integer.valueOf(1), "flow-test",
                "DRAFT", "INACTIVE", "OFF", "operator-test", "operator-test");
    }

    private void executeSchema(Connection schemaConnection) throws IOException, SQLException {
        try (InputStream inputStream = getClass().getResourceAsStream(SCHEMA)) {
            if (inputStream == null) {
                throw new IOException("schema resource not found: " + SCHEMA);
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            String[] statements = new String(outputStream.toByteArray(), StandardCharsets.UTF_8).split(";");
            try (Statement statement = schemaConnection.createStatement()) {
                for (String sql : statements) {
                    String trimmed = sql.trim();
                    if (!trimmed.isEmpty()) {
                        statement.execute(trimmed);
                    }
                }
            }
        }
    }
}

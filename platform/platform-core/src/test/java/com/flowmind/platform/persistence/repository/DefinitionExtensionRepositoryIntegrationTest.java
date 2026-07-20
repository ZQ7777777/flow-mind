package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.persistence.entity.ProcessAttachmentTemplateEntity;
import com.flowmind.platform.persistence.entity.ProcessDefinitionAttachmentConfigEntity;
import com.flowmind.platform.persistence.entity.ProcessFormFieldEntity;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionExtensionRepositoryIntegrationTest {

    private static final String SCHEMA = "/schema/sqlite/001_init_flow_platform.sql";

    private Connection connection;
    private JdbcTemplate jdbcTemplate;
    private ProcessFormFieldRepository formFieldRepository;
    private ProcessAttachmentTemplateRepository attachmentTemplateRepository;
    private ProcessDefinitionAttachmentConfigRepository attachmentConfigRepository;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        executeSchema(connection);
        ExistingConnectionDataSource dataSource = new ExistingConnectionDataSource(connection);
        jdbcTemplate = new JdbcTemplate(dataSource);
        formFieldRepository = new ProcessFormFieldRepository(jdbcTemplate);
        attachmentTemplateRepository = new ProcessAttachmentTemplateRepository(jdbcTemplate);
        attachmentConfigRepository = new ProcessDefinitionAttachmentConfigRepository(jdbcTemplate);
        insertDefinition("definition-source", "definition_extension_source", 1);
        insertDefinition("definition-target", "definition_extension_target", 1);
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void formFieldsCanBeReplacedQueriedCopiedAndDeleted() {
        ProcessFormFieldEntity amount = formField("field-amount", "definition-source", "amount", 20);
        ProcessFormFieldEntity applicant = formField("field-applicant", "definition-source", "applicant", 10);

        formFieldRepository.replaceByDefinitionId("definition-source", Arrays.asList(amount, applicant));

        List<ProcessFormFieldEntity> fields = formFieldRepository.findByDefinitionId("definition-source");
        assertEquals(2, fields.size());
        assertEquals("applicant", fields.get(0).getFieldCode());
        assertEquals("amount", fields.get(1).getFieldCode());

        assertEquals(2, formFieldRepository.copyToDefinition("definition-source", "definition-target"));
        List<ProcessFormFieldEntity> copied = formFieldRepository.findByDefinitionId("definition-target");
        assertEquals(2, copied.size());
        assertNotEquals(fields.get(0).getId(), copied.get(0).getId());
        assertEquals("definition-target", copied.get(0).getDefinitionId());

        assertEquals(2, formFieldRepository.deleteByDefinitionId("definition-source"));
        assertTrue(formFieldRepository.findByDefinitionId("definition-source").isEmpty());
    }

    @Test
    void attachmentTemplateSupportsVersionLookupStatusAndActiveReferenceCheck() {
        attachmentTemplateRepository.insert(template("template-v1", "receipt", 1, "ENABLED"));
        attachmentTemplateRepository.insert(template("template-v2", "receipt", 2, "ENABLED"));

        assertEquals(2, attachmentTemplateRepository.findMaxVersionByAttachmentCode("receipt"));
        assertEquals(0, attachmentTemplateRepository.findMaxVersionByAttachmentCode("unknown"));

        Optional<ProcessAttachmentTemplateEntity> template = attachmentTemplateRepository.findById("template-v1");
        assertTrue(template.isPresent());
        assertEquals("[\"pdf\",\"jpg\"]", template.get().getAllowedExtensions());
        assertEquals(2, attachmentTemplateRepository.findByAttachmentCode("receipt").size());

        assertEquals(1, attachmentTemplateRepository.updateStatus("template-v1", "DISABLED", "tester"));
        assertEquals("DISABLED", attachmentTemplateRepository.findById("template-v1").get().getTemplateStatus());

        attachmentConfigRepository.insert(config("config-row-active", "config-group-active",
                "definition-source", "template-v2", "receipt", "ACTIVE", 10));
        assertTrue(attachmentTemplateRepository.isReferencedByActiveConfig("template-v2"));
        assertFalse(attachmentTemplateRepository.isReferencedByActiveConfig("template-v1"));
    }

    @Test
    void attachmentConfigsCanBeSavedQueriedActivatedCopiedAndDeleted() {
        attachmentTemplateRepository.insert(template("template-receipt", "receipt", 1, "ENABLED"));
        attachmentTemplateRepository.insert(template("template-license", "license", 1, "ENABLED"));
        ProcessDefinitionAttachmentConfigEntity receipt = config("config-row-receipt", "config-group-draft",
                "definition-source", "template-receipt", "receipt", "DRAFT", 20);
        ProcessDefinitionAttachmentConfigEntity license = config("config-row-license", "config-group-draft",
                "definition-source", "template-license", "license", "DRAFT", 10);

        attachmentConfigRepository.replaceDraftGroup("definition-source", "config-group-draft",
                Arrays.asList(receipt, license));

        List<ProcessDefinitionAttachmentConfigEntity> draftConfigs =
                attachmentConfigRepository.findByDefinitionIdAndStatus("definition-source", "DRAFT");
        assertEquals(2, draftConfigs.size());
        assertEquals("license", draftConfigs.get(0).getAttachmentCode());
        assertEquals("receipt", draftConfigs.get(1).getAttachmentCode());

        assertEquals(2, attachmentConfigRepository.activateGroup(
                "definition-source", "config-group-draft", "tester"));
        assertEquals(2, attachmentConfigRepository.findActiveByDefinitionId("definition-source").size());

        attachmentConfigRepository.insert(config("config-row-next", "config-group-next",
                "definition-source", "template-receipt", "receipt", "DRAFT", 30));
        assertEquals(1, attachmentConfigRepository.activateGroup(
                "definition-source", "config-group-next", "tester"));
        assertEquals(1, attachmentConfigRepository.findActiveByDefinitionId("definition-source").size());
        assertEquals("config-group-next", attachmentConfigRepository.findActiveByDefinitionId(
                "definition-source").get(0).getAttachmentConfigId());

        assertEquals(3, attachmentConfigRepository.copyToDefinition("definition-source", "definition-target"));
        List<ProcessDefinitionAttachmentConfigEntity> copied =
                attachmentConfigRepository.findByDefinitionId("definition-target");
        assertEquals(3, copied.size());
        assertNotEquals("config-group-draft", copied.get(0).getAttachmentConfigId());
        assertEquals("definition-target", copied.get(0).getDefinitionId());

        assertEquals(0, attachmentConfigRepository.deleteDraftByDefinitionId("definition-source"));
        assertEquals(3, attachmentConfigRepository.deleteByDefinitionId("definition-source"));
    }

    private ProcessFormFieldEntity formField(String id, String definitionId, String fieldCode, int sortOrder) {
        ProcessFormFieldEntity entity = new ProcessFormFieldEntity();
        entity.setId(id);
        entity.setDefinitionId(definitionId);
        entity.setFieldCode(fieldCode);
        entity.setFieldName(fieldCode + " name");
        entity.setFieldType("string");
        entity.setControlType("input");
        entity.setRequired(Boolean.TRUE);
        entity.setValidationRule("{\"required\":true}");
        entity.setDefaultValue(null);
        entity.setSortOrder(sortOrder);
        return entity;
    }

    private ProcessAttachmentTemplateEntity template(String id, String attachmentCode, int version, String status) {
        ProcessAttachmentTemplateEntity entity = new ProcessAttachmentTemplateEntity();
        entity.setId(id);
        entity.setAttachmentCode(attachmentCode);
        entity.setTemplateVersion(version);
        entity.setAttachmentName(attachmentCode + " name");
        entity.setDescription(attachmentCode + " description");
        entity.setAllowedExtensions("[\"pdf\",\"jpg\"]");
        entity.setMaxSizeBytes(1024L);
        entity.setTemplateStatus(status);
        entity.setCreatedBy("tester");
        entity.setUpdatedBy("tester");
        return entity;
    }

    private ProcessDefinitionAttachmentConfigEntity config(String id, String attachmentConfigId,
                                                           String definitionId, String templateId,
                                                           String attachmentCode, String status,
                                                           int sortOrder) {
        ProcessDefinitionAttachmentConfigEntity entity = new ProcessDefinitionAttachmentConfigEntity();
        entity.setId(id);
        entity.setAttachmentConfigId(attachmentConfigId);
        entity.setDefinitionId(definitionId);
        entity.setConfigStatus(status);
        entity.setAttachmentTemplateId(templateId);
        entity.setAttachmentCode(attachmentCode);
        entity.setRequired(Boolean.TRUE);
        entity.setMinCount(1);
        entity.setMaxCount(3);
        entity.setApplicableNodeCodes("[\"apply\"]");
        entity.setSortOrder(sortOrder);
        entity.setCreatedBy("tester");
        entity.setUpdatedBy("tester");
        return entity;
    }

    private void insertDefinition(String id, String processCode, int version) {
        jdbcTemplate.update("INSERT INTO process_definition "
                        + "(id, process_code, process_name, system_code, version, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, processCode, "Definition Extension Test", "test", version, "tester");
    }

    private static void executeSchema(Connection connection) throws IOException, SQLException {
        String sql;
        try (InputStream input = DefinitionExtensionRepositoryIntegrationTest.class.getResourceAsStream(SCHEMA)) {
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

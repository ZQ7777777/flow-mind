package com.flowmind.platform.core.definition;

import com.flowmind.platform.api.dto.ProcessAttachmentTemplateDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.api.enums.AttachmentTemplateStatusEnum;
import com.flowmind.platform.core.validation.FrozenValidationErrorCodes;
import com.flowmind.platform.core.validation.FrozenValidationException;
import com.flowmind.platform.core.validation.ProcessAttachmentTemplateValidator;
import com.flowmind.platform.persistence.entity.ProcessDefinitionAttachmentConfigEntity;
import com.flowmind.platform.persistence.repository.ProcessAttachmentTemplateRepository;
import com.flowmind.platform.persistence.repository.ProcessDefinitionAttachmentConfigRepository;
import com.flowmind.platform.testsupport.ExistingConnectionDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ProcessAttachmentTemplateManagerIntegrationTest {

    private static final String SCHEMA = "/schema/sqlite/001_init_flow_platform.sql";

    private Connection connection;
    private JdbcTemplate jdbcTemplate;
    private ProcessAttachmentTemplateManager manager;
    private ProcessDefinitionAttachmentConfigRepository attachmentConfigRepository;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        executeSchema(connection);
        jdbcTemplate = new JdbcTemplate(new ExistingConnectionDataSource(connection));
        ProcessAttachmentTemplateRepository templateRepository =
                new ProcessAttachmentTemplateRepository(jdbcTemplate);
        manager = new ProcessAttachmentTemplateManager(templateRepository,
                new ProcessAttachmentTemplateValidator());
        attachmentConfigRepository = new ProcessDefinitionAttachmentConfigRepository(jdbcTemplate);
        insertDefinition("definition-attachment-template", "attachment_template_test");
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void createTemplateVersionIncrementsVersionAndNormalizesExtensions() {
        ProcessAttachmentTemplateDTO first = manager.createTemplateVersion(
                template("receipt", "Receipt", Arrays.asList(".PDF", "jpg", ".pdf", "PNG"), 10L),
                "tester");
        ProcessAttachmentTemplateDTO second = manager.createTemplateVersion(
                template("receipt", "Receipt V2", Arrays.asList("pdf"), 20L),
                "tester");

        assertEquals(1, first.getTemplateVersion());
        assertEquals(Arrays.asList("pdf", "jpg", "png"), first.getAllowedExtensions());
        assertEquals(AttachmentTemplateStatusEnum.ENABLED, first.getTemplateStatus());
        assertEquals(2, second.getTemplateVersion());
    }

    @Test
    void createTemplateVersionRetriesWhenConcurrentVersionConflictHappens() {
        RetryingTemplateRepository retryingRepository = new RetryingTemplateRepository(jdbcTemplate, 2);
        ProcessAttachmentTemplateManager retryingManager = new ProcessAttachmentTemplateManager(retryingRepository,
                new ProcessAttachmentTemplateValidator());

        ProcessAttachmentTemplateDTO created = retryingManager.createTemplateVersion(
                template("receipt", "Receipt", Arrays.asList("pdf"), 10L), "tester");

        assertEquals(3, retryingRepository.getInsertAttempts());
        assertEquals(1, created.getTemplateVersion());
    }

    @Test
    void createTemplateVersionThrowsBusinessExceptionAfterRetryLimitExceeded() {
        RetryingTemplateRepository retryingRepository = new RetryingTemplateRepository(jdbcTemplate, 3);
        ProcessAttachmentTemplateManager retryingManager = new ProcessAttachmentTemplateManager(retryingRepository,
                new ProcessAttachmentTemplateValidator());

        try {
            retryingManager.createTemplateVersion(
                    template("receipt", "Receipt", Arrays.asList("pdf"), 10L), "tester");
            fail("Expected FrozenValidationException.");
        } catch (FrozenValidationException exception) {
            assertEquals(FrozenValidationErrorCodes.ATTACHMENT_TEMPLATE_VERSION_CONFLICT,
                    exception.getErrorCode());
        }
        assertEquals(3, retryingRepository.getInsertAttempts());
    }

    @Test
    void invalidTemplateCreateRequestFailsValidation() {
        ProcessAttachmentTemplateDTO request = template("1bad", "", Collections.singletonList("application/pdf"), 0L);

        ValidationResult result = manager.validateForCreate(request);

        assertFalse(result.isValid());
        assertContainsCode(result, FrozenValidationErrorCodes.ATTACHMENT_TEMPLATE_REQUIRED);
        assertContainsCode(result, FrozenValidationErrorCodes.ATTACHMENT_TEMPLATE_EXTENSION_INVALID);
        assertContainsCode(result, FrozenValidationErrorCodes.ATTACHMENT_TEMPLATE_SIZE_INVALID);
    }

    @Test
    void disableAndQueryTemplateVersions() {
        ProcessAttachmentTemplateDTO first = manager.createTemplateVersion(
                template("receipt", "Receipt", Arrays.asList("pdf"), 10L), "tester");
        manager.createTemplateVersion(template("license", "License", Arrays.asList("jpg"), 10L), "tester");

        ProcessAttachmentTemplateDTO disabled = manager.disableTemplate(first.getAttachmentTemplateId(), "tester");

        assertEquals(AttachmentTemplateStatusEnum.DISABLED, disabled.getTemplateStatus());
        List<ProcessAttachmentTemplateDTO> disabledTemplates =
                manager.findTemplates("receipt", AttachmentTemplateStatusEnum.DISABLED, null);
        assertEquals(1, disabledTemplates.size());
        assertEquals(first.getAttachmentTemplateId(), disabledTemplates.get(0).getAttachmentTemplateId());
        assertEquals(1, manager.findTemplates(null, AttachmentTemplateStatusEnum.ENABLED, null).size());
    }

    @Test
    void unreferencedTemplateCanBeUpdatedInPlace() {
        ProcessAttachmentTemplateDTO created = manager.createTemplateVersion(
                template("receipt", "Receipt", Arrays.asList("pdf"), 10L), "tester");
        ProcessAttachmentTemplateDTO update = template("receipt", "Receipt Updated",
                Arrays.asList(".PDF", "jpg"), 20L);
        update.setAttachmentTemplateId(created.getAttachmentTemplateId());
        update.setTemplateStatus(AttachmentTemplateStatusEnum.DISABLED);

        ProcessAttachmentTemplateDTO updated = manager.updateTemplate(update, "tester");

        assertEquals("Receipt Updated", updated.getAttachmentName());
        assertEquals(Arrays.asList("pdf", "jpg"), updated.getAllowedExtensions());
        assertEquals(20L, updated.getMaxSizeBytes());
        assertEquals(AttachmentTemplateStatusEnum.DISABLED, updated.getTemplateStatus());
    }

    @Test
    void referencedTemplateCannotChangeRulesInPlace() {
        ProcessAttachmentTemplateDTO created = manager.createTemplateVersion(
                template("receipt", "Receipt", Arrays.asList("pdf"), 10L), "tester");
        attachmentConfigRepository.insert(config(created.getAttachmentTemplateId(), "ACTIVE"));
        ProcessAttachmentTemplateDTO update = template("receipt", "Receipt",
                Arrays.asList("pdf", "jpg"), 10L);
        update.setAttachmentTemplateId(created.getAttachmentTemplateId());
        update.setTemplateStatus(AttachmentTemplateStatusEnum.ENABLED);

        try {
            manager.updateTemplate(update, "tester");
            fail("Expected FrozenValidationException.");
        } catch (FrozenValidationException exception) {
            assertEquals(FrozenValidationErrorCodes.ATTACHMENT_TEMPLATE_REFERENCED, exception.getErrorCode());
        }
    }

    @Test
    void inactiveReferencedTemplateCannotChangeRulesInPlace() {
        ProcessAttachmentTemplateDTO created = manager.createTemplateVersion(
                template("receipt", "Receipt", Arrays.asList("pdf"), 10L), "tester");
        attachmentConfigRepository.insert(config(created.getAttachmentTemplateId(), "INACTIVE"));
        ProcessAttachmentTemplateDTO update = template("receipt", "Receipt",
                Arrays.asList("pdf", "jpg"), 10L);
        update.setAttachmentTemplateId(created.getAttachmentTemplateId());
        update.setTemplateStatus(AttachmentTemplateStatusEnum.ENABLED);

        try {
            manager.updateTemplate(update, "tester");
            fail("Expected FrozenValidationException.");
        } catch (FrozenValidationException exception) {
            assertEquals(FrozenValidationErrorCodes.ATTACHMENT_TEMPLATE_REFERENCED, exception.getErrorCode());
        }
    }

    @Test
    void referencedTemplateCanBeDisabledWithoutChangingRules() {
        ProcessAttachmentTemplateDTO created = manager.createTemplateVersion(
                template("receipt", "Receipt", Arrays.asList("pdf"), 10L), "tester");
        attachmentConfigRepository.insert(config(created.getAttachmentTemplateId(), "ACTIVE"));

        ProcessAttachmentTemplateDTO disabled = manager.disableTemplate(created.getAttachmentTemplateId(), "tester");

        assertEquals(AttachmentTemplateStatusEnum.DISABLED, disabled.getTemplateStatus());
    }

    private ProcessAttachmentTemplateDTO template(String code, String name, List<String> extensions, long maxSize) {
        ProcessAttachmentTemplateDTO dto = new ProcessAttachmentTemplateDTO();
        dto.setAttachmentCode(code);
        dto.setAttachmentName(name);
        dto.setDescription(name + " description");
        dto.setAllowedExtensions(extensions);
        dto.setMaxSizeBytes(maxSize);
        dto.setTemplateStatus(AttachmentTemplateStatusEnum.ENABLED);
        return dto;
    }

    private ProcessDefinitionAttachmentConfigEntity config(String templateId, String status) {
        ProcessDefinitionAttachmentConfigEntity entity = new ProcessDefinitionAttachmentConfigEntity();
        entity.setId("config-row-" + status.toLowerCase());
        entity.setAttachmentConfigId("config-group-" + status.toLowerCase());
        entity.setDefinitionId("definition-attachment-template");
        entity.setConfigStatus(status);
        entity.setAttachmentTemplateId(templateId);
        entity.setAttachmentCode("receipt");
        entity.setRequired(Boolean.TRUE);
        entity.setMinCount(1);
        entity.setMaxCount(3);
        entity.setApplicableNodeCodes("[\"apply\"]");
        entity.setSortOrder(10);
        entity.setCreatedBy("tester");
        entity.setUpdatedBy("tester");
        return entity;
    }

    private void insertDefinition(String id, String processCode) {
        jdbcTemplate.update("INSERT INTO process_definition "
                        + "(id, process_code, process_name, system_code, version, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, processCode, "Attachment Template Test", "test", 1, "tester");
    }

    private static void executeSchema(Connection connection) throws IOException, SQLException {
        String sql;
        try (InputStream input = ProcessAttachmentTemplateManagerIntegrationTest.class.getResourceAsStream(SCHEMA)) {
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

    private void assertContainsCode(ValidationResult result, String expectedCode) {
        for (ValidationResult.Issue issue : result.getIssues()) {
            if (expectedCode.equals(issue.getCode())) {
                return;
            }
        }
        throw new AssertionError("Expected issue code " + expectedCode + " but got " + result.getIssues());
    }

    private static final class RetryingTemplateRepository extends ProcessAttachmentTemplateRepository {

        private final int conflictsBeforeSuccess;
        private int insertAttempts;

        private RetryingTemplateRepository(JdbcTemplate jdbcTemplate, int conflictsBeforeSuccess) {
            super(jdbcTemplate);
            this.conflictsBeforeSuccess = conflictsBeforeSuccess;
        }

        @Override
        public int insert(com.flowmind.platform.persistence.entity.ProcessAttachmentTemplateEntity entity) {
            insertAttempts++;
            if (insertAttempts <= conflictsBeforeSuccess) {
                throw new DuplicateKeyException("Simulated concurrent version conflict.");
            }
            return super.insert(entity);
        }

        private int getInsertAttempts() {
            return insertAttempts;
        }
    }
}

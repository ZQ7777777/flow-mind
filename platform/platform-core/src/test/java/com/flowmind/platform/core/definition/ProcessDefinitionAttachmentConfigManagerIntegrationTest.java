package com.flowmind.platform.core.definition;

import com.flowmind.platform.api.dto.ProcessAttachmentConfigDTO;
import com.flowmind.platform.api.dto.ProcessAttachmentTemplateDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.api.enums.AttachmentConfigStatusEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.core.validation.FrozenValidationErrorCodes;
import com.flowmind.platform.core.validation.ProcessDefinitionAttachmentConfigValidator;
import com.flowmind.platform.persistence.entity.ProcessAttachmentTemplateEntity;
import com.flowmind.platform.persistence.repository.ProcessAttachmentTemplateRepository;
import com.flowmind.platform.persistence.repository.ProcessDefinitionAttachmentConfigRepository;
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

class ProcessDefinitionAttachmentConfigManagerIntegrationTest {

    private static final String SCHEMA = "/schema/sqlite/001_init_flow_platform.sql";

    private Connection connection;
    private JdbcTemplate jdbcTemplate;
    private ProcessDefinitionAttachmentConfigManager manager;
    private ProcessAttachmentTemplateRepository templateRepository;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        executeSchema(connection);
        jdbcTemplate = new JdbcTemplate(new ExistingConnectionDataSource(connection));
        templateRepository = new ProcessAttachmentTemplateRepository(jdbcTemplate);
        ProcessDefinitionAttachmentConfigRepository configRepository =
                new ProcessDefinitionAttachmentConfigRepository(jdbcTemplate);
        manager = new ProcessDefinitionAttachmentConfigManager(configRepository, templateRepository,
                new ProcessDefinitionAttachmentConfigValidator(templateRepository));
        insertDefinition("definition-source", "attachment_config_source");
        insertDefinition("definition-target", "attachment_config_target");
        templateRepository.insert(template("template-receipt", "receipt", "ENABLED"));
        templateRepository.insert(template("template-license", "license", "ENABLED"));
        templateRepository.insert(template("template-disabled", "disabled", "DISABLED"));
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void draftGroupCanBeSavedAndQueriedAsMergedTemplateView() {
        ValidationResult result = manager.saveDraftGroup("definition-source", "config-group-draft",
                Arrays.asList(config("template-receipt", "receipt", 20),
                        config("template-license", "license", 10)),
                userTaskNodes(), "tester");

        assertTrue(result.isValid());
        List<ProcessAttachmentTemplateDTO> draft = manager.findDraftByDefinitionId("definition-source");
        assertEquals(2, draft.size());
        assertEquals("license", draft.get(0).getAttachmentCode());
        assertEquals("receipt", draft.get(1).getAttachmentCode());
        assertEquals("config-group-draft", draft.get(0).getAttachmentConfigId());
        assertEquals(Arrays.asList("pdf", "jpg"), draft.get(0).getAllowedExtensions());
        assertEquals(Collections.singletonList("apply"), draft.get(0).getApplicableNodeCodes());
    }

    @Test
    void draftGroupTrimsTemplateCodeAndNodeBeforeValidationAndSave() {
        ProcessAttachmentConfigDTO receipt = config(" template-receipt ", " receipt ", 10);
        receipt.setApplicableNodeCodes(Collections.singletonList(" apply "));

        ValidationResult result = manager.saveDraftGroup("definition-source", "config-group-trim",
                Collections.singletonList(receipt), userTaskNodes(), "tester");

        assertTrue(result.isValid());
        List<ProcessAttachmentTemplateDTO> draft = manager.findDraftByDefinitionId("definition-source");
        assertEquals(1, draft.size());
        assertEquals("template-receipt", draft.get(0).getAttachmentTemplateId());
        assertEquals("receipt", draft.get(0).getAttachmentCode());
        assertEquals(Collections.singletonList("apply"), draft.get(0).getApplicableNodeCodes());
    }

    @Test
    void invalidDraftGroupIsRejectedAndNotPersisted() {
        ProcessAttachmentConfigDTO disabled = config("template-disabled", "disabled", 10);
        ProcessAttachmentConfigDTO duplicate = config("template-receipt", "receipt", 20);
        ProcessAttachmentConfigDTO duplicateAgain = config("template-receipt", "receipt", 30);
        ProcessAttachmentConfigDTO badQuantity = config("template-license", "license", 40);
        badQuantity.setRequired(Boolean.TRUE);
        badQuantity.setMinCount(0);
        badQuantity.setMaxCount(-1);
        ProcessAttachmentConfigDTO badNode = config("missing-template", "missing", 50);
        badNode.setApplicableNodeCodes(Collections.singletonList("missing"));

        ValidationResult result = manager.saveDraftGroup("definition-source", "config-group-invalid",
                Arrays.asList(disabled, duplicate, duplicateAgain, badQuantity, badNode),
                userTaskNodes(), "tester");

        assertFalse(result.isValid());
        assertContainsCode(result, FrozenValidationErrorCodes.ATTACHMENT_CONFIG_TEMPLATE_DISABLED);
        assertContainsCode(result, FrozenValidationErrorCodes.ATTACHMENT_CONFIG_DUPLICATED);
        assertContainsCode(result, FrozenValidationErrorCodes.ATTACHMENT_CONFIG_QUANTITY_INVALID);
        assertContainsCode(result, FrozenValidationErrorCodes.ATTACHMENT_CONFIG_TEMPLATE_INVALID);
        assertContainsCode(result, FrozenValidationErrorCodes.ATTACHMENT_CONFIG_NODE_INVALID);
        assertTrue(manager.findDraftByDefinitionId("definition-source").isEmpty());
    }

    @Test
    void draftGroupRejectsConfigGroupIdDifferentFromMethodArgument() {
        ProcessAttachmentConfigDTO receipt = config("template-receipt", "receipt", 10);
        receipt.setAttachmentConfigId("config-group-other");

        ValidationResult result = manager.saveDraftGroup("definition-source", "config-group-target",
                Collections.singletonList(receipt), userTaskNodes(), "tester");

        assertFalse(result.isValid());
        assertContainsCode(result, FrozenValidationErrorCodes.ATTACHMENT_CONFIG_REQUIRED);
        assertTrue(manager.findDraftByDefinitionId("definition-source").isEmpty());
    }

    @Test
    void draftGroupRejectsMixedConfigGroupIdsInConfigList() {
        ProcessAttachmentConfigDTO receipt = config("template-receipt", "receipt", 10);
        receipt.setAttachmentConfigId("config-group-first");
        ProcessAttachmentConfigDTO license = config("template-license", "license", 20);
        license.setAttachmentConfigId("config-group-second");

        ValidationResult result = manager.saveDraftGroup("definition-source", null,
                Arrays.asList(receipt, license), userTaskNodes(), "tester");

        assertFalse(result.isValid());
        assertContainsCode(result, FrozenValidationErrorCodes.ATTACHMENT_CONFIG_REQUIRED);
        assertTrue(manager.findDraftByDefinitionId("definition-source").isEmpty());
    }

    @Test
    void emptyDraftGroupClearsExistingDraftConfigs() {
        manager.saveDraftGroup("definition-source", "config-group-draft",
                Collections.singletonList(config("template-receipt", "receipt", 10)),
                userTaskNodes(), "tester");

        ValidationResult result = manager.saveDraftGroup("definition-source", "config-group-draft",
                Collections.emptyList(), userTaskNodes(), "tester");

        assertTrue(result.isValid());
        assertTrue(manager.findDraftByDefinitionId("definition-source").isEmpty());
    }

    @Test
    void nullDraftGroupConfigsAreHandledAsEmptyList() {
        ValidationResult result = manager.saveDraftGroup("definition-source", null,
                null, userTaskNodes(), "tester");

        assertTrue(result.isValid());
        assertTrue(manager.findDraftByDefinitionId("definition-source").isEmpty());
    }

    @Test
    void multipleDraftGroupsCanCoexistForSameDefinition() {
        manager.saveDraftGroup("definition-source", "config-group-first",
                Collections.singletonList(config("template-receipt", "receipt", 10)),
                userTaskNodes(), "tester");
        manager.saveDraftGroup("definition-source", "config-group-second",
                Collections.singletonList(config("template-license", "license", 20)),
                userTaskNodes(), "tester");

        List<ProcessAttachmentTemplateDTO> draft = manager.findDraftByDefinitionId("definition-source");

        assertEquals(2, draft.size());
        assertTrue(containsStatus(draft, "config-group-first", AttachmentConfigStatusEnum.DRAFT));
        assertTrue(containsStatus(draft, "config-group-second", AttachmentConfigStatusEnum.DRAFT));
    }

    @Test
    void activateDraftGroupActivatesOnlyTheUniqueDraftGroup() {
        manager.saveDraftGroup("definition-source", "config-group-draft",
                Collections.singletonList(config("template-receipt", "receipt", 10)),
                userTaskNodes(), "tester");

        ValidationResult result = manager.activateDraftGroup("definition-source", userTaskNodes(), "tester");

        assertTrue(result.isValid());
        List<ProcessAttachmentTemplateDTO> active = manager.findActiveByDefinitionId("definition-source");
        assertEquals(1, active.size());
        assertEquals("config-group-draft", active.get(0).getAttachmentConfigId());
    }

    @Test
    void activateDraftGroupRejectsAmbiguousDraftGroupsWithoutChangingTheirStatus() {
        manager.saveDraftGroup("definition-source", "config-group-first",
                Collections.singletonList(config("template-receipt", "receipt", 10)),
                userTaskNodes(), "tester");
        manager.saveDraftGroup("definition-source", "config-group-second",
                Collections.singletonList(config("template-license", "license", 20)),
                userTaskNodes(), "tester");

        ValidationResult result = manager.activateDraftGroup("definition-source", userTaskNodes(), "tester");

        assertFalse(result.isValid());
        assertTrue(containsStatus(manager.findDraftByDefinitionId("definition-source"),
                "config-group-first", AttachmentConfigStatusEnum.DRAFT));
        assertTrue(containsStatus(manager.findDraftByDefinitionId("definition-source"),
                "config-group-second", AttachmentConfigStatusEnum.DRAFT));
        assertTrue(manager.findActiveByDefinitionId("definition-source").isEmpty());
    }

    @Test
    void emptyDraftGroupClearsOnlySpecifiedDraftGroup() {
        manager.saveDraftGroup("definition-source", "config-group-first",
                Collections.singletonList(config("template-receipt", "receipt", 10)),
                userTaskNodes(), "tester");
        manager.saveDraftGroup("definition-source", "config-group-second",
                Collections.singletonList(config("template-license", "license", 20)),
                userTaskNodes(), "tester");

        ValidationResult result = manager.saveDraftGroup("definition-source", "config-group-first",
                Collections.emptyList(), userTaskNodes(), "tester");

        List<ProcessAttachmentTemplateDTO> draft = manager.findDraftByDefinitionId("definition-source");
        assertTrue(result.isValid());
        assertEquals(1, draft.size());
        assertTrue(containsStatus(draft, "config-group-second", AttachmentConfigStatusEnum.DRAFT));
    }

    @Test
    void activatingNewGroupMakesOldActiveGroupInactive() {
        manager.saveDraftGroup("definition-source", "config-group-first",
                Collections.singletonList(config("template-receipt", "receipt", 10)),
                userTaskNodes(), "tester");
        assertTrue(manager.activateGroup("definition-source", "config-group-first",
                userTaskNodes(), "tester").isValid());

        manager.saveDraftGroup("definition-source", "config-group-second",
                Collections.singletonList(config("template-license", "license", 10)),
                userTaskNodes(), "tester");
        assertTrue(manager.activateGroup("definition-source", "config-group-second",
                userTaskNodes(), "tester").isValid());

        List<ProcessAttachmentTemplateDTO> active = manager.findActiveByDefinitionId("definition-source");
        assertEquals(1, active.size());
        assertEquals("config-group-second", active.get(0).getAttachmentConfigId());
        List<ProcessAttachmentTemplateDTO> all = manager.findByDefinitionId("definition-source");
        assertEquals(2, all.size());
        assertTrue(containsStatus(all, "config-group-first", AttachmentConfigStatusEnum.INACTIVE));
    }

    @Test
    void activatingMissingGroupIsRejected() {
        ValidationResult result = manager.activateGroup("definition-source", "config-group-missing",
                userTaskNodes(), "tester");

        assertFalse(result.isValid());
        assertContainsCode(result, FrozenValidationErrorCodes.ATTACHMENT_CONFIG_REQUIRED);
    }

    @Test
    void activatingGroupFromAnotherDefinitionIsRejected() {
        manager.saveDraftGroup("definition-source", "config-group-source-only",
                Collections.singletonList(config("template-receipt", "receipt", 10)),
                userTaskNodes(), "tester");

        ValidationResult result = manager.activateGroup("definition-target", "config-group-source-only",
                userTaskNodes(), "tester");

        assertFalse(result.isValid());
        assertContainsCode(result, FrozenValidationErrorCodes.ATTACHMENT_CONFIG_REQUIRED);
        assertTrue(manager.findActiveByDefinitionId("definition-target").isEmpty());
    }

    @Test
    void copiedConfigsUseNewDraftGroupAndRows() {
        manager.saveDraftGroup("definition-source", "config-group-source",
                Arrays.asList(config("template-receipt", "receipt", 10),
                        config("template-license", "license", 20)),
                userTaskNodes(), "tester");
        List<ProcessAttachmentTemplateDTO> source = manager.findDraftByDefinitionId("definition-source");

        assertEquals(2, manager.copyAttachmentConfigs("definition-source", "definition-target"));

        List<ProcessAttachmentTemplateDTO> copied = manager.findDraftByDefinitionId("definition-target");
        assertEquals(2, copied.size());
        assertEquals(AttachmentConfigStatusEnum.DRAFT, copied.get(0).getConfigStatus());
        assertNotEquals(source.get(0).getId(), copied.get(0).getId());
        assertNotEquals(source.get(0).getAttachmentConfigId(), copied.get(0).getAttachmentConfigId());
    }

    private boolean containsStatus(List<ProcessAttachmentTemplateDTO> configs,
                                   String groupId,
                                   AttachmentConfigStatusEnum status) {
        for (ProcessAttachmentTemplateDTO config : configs) {
            if (groupId.equals(config.getAttachmentConfigId()) && status.equals(config.getConfigStatus())) {
                return true;
            }
        }
        return false;
    }

    private ProcessAttachmentConfigDTO config(String templateId, String attachmentCode, int sortOrder) {
        ProcessAttachmentConfigDTO dto = new ProcessAttachmentConfigDTO();
        dto.setAttachmentTemplateId(templateId);
        dto.setAttachmentCode(attachmentCode);
        dto.setRequired(Boolean.TRUE);
        dto.setMinCount(1);
        dto.setMaxCount(3);
        dto.setApplicableNodeCodes(Collections.singletonList("apply"));
        dto.setSortOrder(sortOrder);
        return dto;
    }

    private ProcessAttachmentTemplateEntity template(String id, String attachmentCode, String status) {
        ProcessAttachmentTemplateEntity entity = new ProcessAttachmentTemplateEntity();
        entity.setId(id);
        entity.setAttachmentCode(attachmentCode);
        entity.setTemplateVersion(1);
        entity.setAttachmentName(attachmentCode + " name");
        entity.setDescription(attachmentCode + " description");
        entity.setAllowedExtensions("[\"pdf\",\"jpg\"]");
        entity.setMaxSizeBytes(1024L);
        entity.setTemplateStatus(status);
        entity.setCreatedBy("tester");
        entity.setUpdatedBy("tester");
        return entity;
    }

    private List<ProcessNodeDTO> userTaskNodes() {
        ProcessNodeDTO start = node("start", NodeTypeEnum.START);
        ProcessNodeDTO apply = node("apply", NodeTypeEnum.USER_TASK);
        ProcessNodeDTO end = node("end", NodeTypeEnum.END);
        return Arrays.asList(start, apply, end);
    }

    private ProcessNodeDTO node(String nodeCode, NodeTypeEnum nodeType) {
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setNodeCode(nodeCode);
        node.setNodeName(nodeCode);
        node.setNodeType(nodeType);
        return node;
    }

    private void insertDefinition(String id, String processCode) {
        jdbcTemplate.update("INSERT INTO process_definition "
                        + "(id, process_code, process_name, system_code, version, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, processCode, "Attachment Config Test", "test", 1, "tester");
    }

    private static void executeSchema(Connection connection) throws IOException, SQLException {
        String sql;
        try (InputStream input = ProcessDefinitionAttachmentConfigManagerIntegrationTest.class
                .getResourceAsStream(SCHEMA)) {
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
}

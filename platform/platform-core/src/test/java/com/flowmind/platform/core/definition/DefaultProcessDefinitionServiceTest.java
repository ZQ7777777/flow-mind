package com.flowmind.platform.core.definition;

import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.OperationResult;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.dto.ProcessAttachmentConfigDTO;
import com.flowmind.platform.api.dto.ProcessAttachmentTemplateDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionQuery;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessFormFieldDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.api.enums.ActivationStatusEnum;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.AttachmentConfigStatusEnum;
import com.flowmind.platform.api.enums.DefinitionStatusEnum;
import com.flowmind.platform.api.enums.GrayStatusEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.api.enums.OperationTargetTypeEnum;
import com.flowmind.platform.api.request.CopyProcessDefinitionRequest;
import com.flowmind.platform.api.request.CreateProcessDefinitionRequest;
import com.flowmind.platform.api.request.DefinitionOperationRequest;
import com.flowmind.platform.api.request.GrayReleaseRequest;
import com.flowmind.platform.api.request.SaveProcessGraphRequest;
import com.flowmind.platform.api.request.StartProcessRequest;
import com.flowmind.platform.api.service.AttachmentService;
import com.flowmind.platform.api.service.CallbackService;
import com.flowmind.platform.core.runtime.DefaultProcessRuntimeService;
import com.flowmind.platform.core.runtime.RuntimeDefinitionLoader;
import com.flowmind.platform.core.runtime.RuntimeNodeAdvancer;
import com.flowmind.platform.core.runtime.RuntimeOperationExecutor;
import com.flowmind.platform.core.runtime.RuntimeRequestValidator;
import com.flowmind.platform.core.validation.DefinitionRequestValidator;
import com.flowmind.platform.core.validation.FrozenValidationErrorCodes;
import com.flowmind.platform.core.validation.ProcessDefinitionAttachmentConfigValidator;
import com.flowmind.platform.core.validation.ProcessFormFieldValidator;
import com.flowmind.platform.persistence.entity.ProcessDefinitionEntity;
import com.flowmind.platform.persistence.entity.ProcessEdgeEntity;
import com.flowmind.platform.persistence.entity.ProcessNodeEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.HistoryTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessAttachmentTemplateRepository;
import com.flowmind.platform.persistence.repository.ProcessDefinitionAttachmentConfigRepository;
import com.flowmind.platform.persistence.repository.ProcessDefinitionRepository;
import com.flowmind.platform.persistence.repository.ProcessEdgeRepository;
import com.flowmind.platform.persistence.repository.ProcessFormFieldRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import com.flowmind.platform.persistence.repository.ProcessNodeRepository;
import com.flowmind.platform.persistence.repository.ProcessOperationRecordRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultProcessDefinitionServiceTest {

    private static final String SCHEMA = "/schema/sqlite/001_init_flow_platform.sql";

    private Connection connection;
    private JdbcTemplate jdbcTemplate;
    private ProcessDefinitionRepository definitionRepository;
    private ProcessNodeRepository nodeRepository;
    private ProcessEdgeRepository edgeRepository;
    private ProcessFormFieldRepository formFieldRepository;
    private ProcessAttachmentTemplateRepository attachmentTemplateRepository;
    private ProcessDefinitionAttachmentConfigRepository attachmentConfigRepository;
    private ProcessFormFieldDefinitionManager formFieldManager;
    private ProcessDefinitionAttachmentConfigManager attachmentConfigManager;
    private TransactionTemplate transactionTemplate;
    private RecordingProcessDefinitionCache processDefinitionCache;
    private DefaultProcessDefinitionService service;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        executeSchema(connection);
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
        jdbcTemplate = new JdbcTemplate(dataSource);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        definitionRepository = new ProcessDefinitionRepository(jdbcTemplate);
        nodeRepository = new ProcessNodeRepository(jdbcTemplate);
        edgeRepository = new ProcessEdgeRepository(jdbcTemplate);
        formFieldRepository = new ProcessFormFieldRepository(jdbcTemplate);
        attachmentTemplateRepository = new ProcessAttachmentTemplateRepository(jdbcTemplate);
        attachmentConfigRepository = new ProcessDefinitionAttachmentConfigRepository(jdbcTemplate);
        formFieldManager = new ProcessFormFieldDefinitionManager(formFieldRepository,
                new ProcessFormFieldValidator());
        attachmentConfigManager = new ProcessDefinitionAttachmentConfigManager(attachmentConfigRepository,
                attachmentTemplateRepository,
                new ProcessDefinitionAttachmentConfigValidator(attachmentTemplateRepository));
        processDefinitionCache = new RecordingProcessDefinitionCache();
        service = new DefaultProcessDefinitionService(definitionRepository,
                nodeRepository,
                edgeRepository,
                formFieldManager,
                attachmentConfigManager,
                new ProcessOperationRecordRepository(jdbcTemplate),
                processDefinitionCache);
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void createDefinitionAllocatesVersionAndFixedInitialStatuses() {
        ProcessDefinitionDTO first = service.createDefinition(createRequest("operation-001", "deposit"));
        ProcessDefinitionDTO second = service.createDefinition(createRequest("operation-002", "deposit"));

        assertEquals(Integer.valueOf(1), first.getVersion());
        assertEquals(Integer.valueOf(2), second.getVersion());
        assertEquals(DefinitionStatusEnum.DRAFT, first.getDefinitionStatus());
        assertEquals(ActivationStatusEnum.INACTIVE, first.getActivationStatus());
        assertEquals(GrayStatusEnum.OFF, first.getGrayStatus());
        assertEquals("operator-001", first.getCreatedBy());
        assertEquals("operator-001", first.getUpdatedBy());
    }

    @Test
    void createDefinitionReplaysSameOperationAndRejectsDifferentRequestHash() {
        ProcessDefinitionDTO first = service.createDefinition(createRequest("operation-001", "deposit"));
        ProcessDefinitionDTO replay = service.createDefinition(createRequest("operation-001", "deposit"));

        assertEquals(first.getId(), replay.getId());
        assertEquals(Integer.valueOf(1), replay.getVersion());

        assertThrows(IllegalArgumentException.class,
                () -> service.createDefinition(createRequest("operation-001", "changed")));
    }

    @Test
    void getDefinitionReturnsDefinitionNodesEdgesAndExtensions() {
        ProcessDefinitionDTO created = service.createDefinition(createRequest("operation-001", "deposit"));
        nodeRepository.batchInsert(Arrays.asList(
                node("node-apply", created.getId(), "apply", 20),
                node("node-start", created.getId(), "start", 10)));
        edgeRepository.batchInsert(Arrays.asList(
                edge("edge-apply-end", created.getId(), "apply", "end", 20),
                edge("edge-start-apply", created.getId(), "start", "apply", 10)));
        insertFormField(created.getId());
        insertAttachmentTemplate();
        insertAttachmentConfig(created.getId());

        ProcessDefinitionDetailDTO detail = service.getDefinition(created.getId());

        assertEquals(created.getId(), detail.getId());
        assertEquals(2, detail.getNodes().size());
        assertEquals("start", detail.getNodes().get(0).getNodeCode());
        assertEquals(2, detail.getEdges().size());
        assertEquals("edge-start-apply", detail.getEdges().get(0).getEdgeCode());
        assertEquals(1, detail.getFormFields().size());
        assertEquals("amount", detail.getFormFields().get(0).getFieldCode());
        assertEquals(1, detail.getAttachmentTemplates().size());
        assertEquals("receipt", detail.getAttachmentTemplates().get(0).getAttachmentCode());
    }

    @Test
    void saveGraphPersistsDepositFiveNodeGraphAndExtensionsInStableOrder() {
        ProcessDefinitionDTO created = service.createDefinition(createRequest("operation-001", "deposit"));
        insertAttachmentTemplate();

        ProcessDefinitionDTO saved = saveGraph(created.getId(), depositGraph("operation-save-001"));
        ProcessDefinitionDetailDTO detail = service.getDefinition(created.getId());

        assertEquals(created.getId(), saved.getId());
        assertEquals("operator-save", saved.getUpdatedBy());
        assertEquals(Arrays.asList("start", "apply", "manager", "finance", "end"), nodeCodes(detail));
        assertEquals(Arrays.asList("edge-start-apply", "edge-apply-manager",
                "edge-manager-finance", "edge-finance-end"), edgeCodes(detail));
        assertEquals(1, detail.getFormFields().size());
        assertEquals("amount", detail.getFormFields().get(0).getFieldCode());
        assertEquals(1, detail.getAttachmentTemplates().size());
        assertEquals("receipt", detail.getAttachmentTemplates().get(0).getAttachmentCode());
        assertEquals(Collections.singletonList(created.getId()), processDefinitionCache.definitionIds);
    }

    @Test
    void saveGraphPersistsInvalidDraftAndDefersEdgeReferenceToPublishValidation() {
        ProcessDefinitionDTO created = service.createDefinition(createRequest("operation-001", "deposit"));
        insertAttachmentTemplate();

        SaveProcessGraphRequest request = depositGraph("operation-save-001");
        request.getEdges().get(3).setTargetNodeCode("missing");

        saveGraph(created.getId(), request);

        assertEquals(5, nodeRepository.findByDefinitionId(created.getId()).size());
        assertEquals("missing", service.getDefinition(created.getId()).getEdges().get(3).getTargetNodeCode());
        ValidationResult first = service.validateForPublish(created.getId());
        ValidationResult second = service.validateForPublish(created.getId());

        assertTrue(!first.isValid());
        assertTrue(hasIssue(first, FrozenValidationErrorCodes.MODEL_EDGE_REFERENCE_INVALID,
                "missing", "edge-finance-end"));
        assertEquals(first, second);
    }

    @Test
    void validateForPublishUsesDefinitionDetailsPersistedByAAndC() {
        ProcessDefinitionDTO created = service.createDefinition(createRequest("operation-001", "deposit"));
        insertAttachmentTemplate();
        saveGraph(created.getId(), depositGraph("operation-save-001"));

        ValidationResult result = service.validateForPublish(created.getId());

        assertTrue(result.isValid());
        assertTrue(result.getIssues().isEmpty());
    }

    @Test
    void publishValidDraftAndReplayWithoutInvalidatingCacheAgain() {
        ProcessDefinitionDTO created = service.createDefinition(createRequest("operation-001", "deposit"));
        insertAttachmentTemplate();
        saveGraph(created.getId(), depositGraph("operation-save-001"));
        processDefinitionCache.clear();

        ProcessDefinitionDTO published = publish(lifecycleRequest(created.getId(), "operation-publish-001"));
        ProcessDefinitionDTO replay = publish(lifecycleRequest(created.getId(), "operation-publish-001"));

        assertEquals(created.getId(), published.getId());
        assertEquals(DefinitionStatusEnum.PUBLISHED, published.getDefinitionStatus());
        assertEquals(ActivationStatusEnum.INACTIVE, published.getActivationStatus());
        assertEquals(GrayStatusEnum.OFF, published.getGrayStatus());
        assertEquals(published.getId(), replay.getId());
        assertEquals(1L, countRowsByOperationId("operation-publish-001"));
        assertEquals(1L, countAuditRows("operation-publish-001", "DEFINITION_PUBLISH"));
        assertEquals(Collections.singletonList(created.getId()), processDefinitionCache.definitionIds);
    }

    @Test
    void publishRejectsInvalidDraftAndDoesNotPersistSuccessSideEffects() {
        ProcessDefinitionDTO created = service.createDefinition(createRequest("operation-001", "deposit"));
        saveGraph(created.getId(), simpleLinearGraph("operation-save-001"));
        jdbcTemplate.update("DELETE FROM process_edge WHERE definition_id = ?", created.getId());
        processDefinitionCache.clear();

        assertThrows(IllegalArgumentException.class,
                () -> publish(lifecycleRequest(created.getId(), "operation-publish-001")));

        ProcessDefinitionEntity persisted = definitionRepository.findById(created.getId());
        assertEquals(DefinitionStatusEnum.DRAFT.name(), persisted.getDefinitionStatus());
        assertEquals(0L, countRowsByOperationId("operation-publish-001"));
        assertEquals(0L, countAuditRows("operation-publish-001", "DEFINITION_PUBLISH"));
        assertEquals(0, processDefinitionCache.definitionIds.size());
    }

    @Test
    void activatePublishedDefinitionDeactivatesOldFullVersionAndInvalidatesBothCaches() {
        ProcessDefinitionDTO first = service.createDefinition(createRequest("operation-001", "deposit"));
        saveGraph(first.getId(), simpleLinearGraph("operation-save-001"));
        publish(lifecycleRequest(first.getId(), "operation-publish-001"));
        activate(lifecycleRequest(first.getId(), "operation-activate-001"));
        ProcessDefinitionDTO second = copyDefinition(first.getId(), copyRequest("operation-copy-001"));
        saveGraph(second.getId(), simpleLinearGraph("operation-save-002"));
        publish(lifecycleRequest(second.getId(), "operation-publish-002"));
        processDefinitionCache.clear();

        ProcessDefinitionDTO activated = activate(lifecycleRequest(second.getId(), "operation-activate-002"));

        assertEquals(ActivationStatusEnum.ACTIVE, activated.getActivationStatus());
        assertEquals(ActivationStatusEnum.INACTIVE.name(),
                definitionRepository.findById(first.getId()).getActivationStatus());
        assertEquals(second.getId(), definitionRepository.findActiveFullByProcessCode("deposit").getId());
        assertTrue(processDefinitionCache.definitionIds.contains(first.getId()));
        assertTrue(processDefinitionCache.definitionIds.contains(second.getId()));
        assertEquals(1L, countAuditRows("operation-activate-002", "DEFINITION_ACTIVATE"));
    }

    @Test
    void deactivateActiveDefinitionKeepsExistingInstanceAndStopsNewFullVersionSelection() {
        ProcessDefinitionDTO created = service.createDefinition(createRequest("operation-001", "deposit"));
        saveGraph(created.getId(), simpleLinearGraph("operation-save-001"));
        publish(lifecycleRequest(created.getId(), "operation-publish-001"));
        activate(lifecycleRequest(created.getId(), "operation-activate-001"));
        insertRuntimeData(created.getId());
        processDefinitionCache.clear();

        ProcessDefinitionDTO deactivated = deactivate(lifecycleRequest(created.getId(), "operation-deactivate-001"));

        assertEquals(ActivationStatusEnum.INACTIVE, deactivated.getActivationStatus());
        assertNull(definitionRepository.findActiveFullByProcessCode("deposit"));
        assertEquals(1L, countRows("process_instance"));
        assertEquals(1L, countAuditRows("operation-deactivate-001", "DEFINITION_DEACTIVATE"));
        assertEquals(Collections.singletonList(created.getId()), processDefinitionCache.definitionIds);
    }

    @Test
    void activatePublishedDefinitionPromotesAttachmentGroupForRuntimeInstanceFreezing() {
        ProcessDefinitionDTO created = service.createDefinition(createRequest("operation-001", "deposit"));
        insertAttachmentTemplate();
        saveGraph(created.getId(), depositGraph("operation-save-001"));
        publish(lifecycleRequest(created.getId(), "operation-publish-001"));

        activate(lifecycleRequest(created.getId(), "operation-activate-001"));

        ProcessDefinitionDetailDTO activated = service.getDefinition(created.getId());
        assertEquals(1, activated.getAttachmentTemplates().size());
        ProcessAttachmentTemplateDTO attachment = activated.getAttachmentTemplates().get(0);
        assertEquals(AttachmentConfigStatusEnum.ACTIVE, attachment.getConfigStatus());
        assertEquals("attachment-group-001", attachment.getAttachmentConfigId());
    }

    @Test
    void activateRollsBackAttachmentGroupWhenDefinitionActivationFails() {
        ProcessDefinitionDTO created = service.createDefinition(createRequest("operation-001", "deposit"));
        insertAttachmentTemplate();
        saveGraph(created.getId(), depositGraph("operation-save-001"));
        publish(lifecycleRequest(created.getId(), "operation-publish-001"));
        processDefinitionCache.clear();
        DefaultProcessDefinitionService failingService = new DefaultProcessDefinitionService(
                new FailingActivationDefinitionRepository(jdbcTemplate), nodeRepository, edgeRepository,
                formFieldManager, attachmentConfigManager, new ProcessOperationRecordRepository(jdbcTemplate),
                processDefinitionCache);

        assertThrows(IllegalStateException.class,
                () -> activate(failingService, lifecycleRequest(created.getId(), "operation-activate-001")));

        assertEquals(AttachmentConfigStatusEnum.DRAFT,
                service.getDefinition(created.getId()).getAttachmentTemplates().get(0).getConfigStatus());
        assertEquals(ActivationStatusEnum.INACTIVE.name(),
                definitionRepository.findById(created.getId()).getActivationStatus());
        assertEquals(0L, countRowsByOperationId("operation-activate-001"));
        assertEquals(0, processDefinitionCache.definitionIds.size());
    }

    @Test
    void activateRejectsDefinitionWhenAttachmentGroupUpdateDoesNotAffectEveryRow() {
        ProcessDefinitionDTO created = service.createDefinition(createRequest("operation-001", "deposit"));
        insertAttachmentTemplate();
        saveGraph(created.getId(), depositGraph("operation-save-001"));
        publish(lifecycleRequest(created.getId(), "operation-publish-001"));
        processDefinitionCache.clear();
        ProcessDefinitionAttachmentConfigManager failingAttachmentManager =
                new ProcessDefinitionAttachmentConfigManager(
                        new NoRowsActivatedAttachmentConfigRepository(jdbcTemplate), attachmentTemplateRepository,
                        new ProcessDefinitionAttachmentConfigValidator(attachmentTemplateRepository));
        DefaultProcessDefinitionService failingService = new DefaultProcessDefinitionService(definitionRepository,
                nodeRepository, edgeRepository, formFieldManager, failingAttachmentManager,
                new ProcessOperationRecordRepository(jdbcTemplate), processDefinitionCache);

        assertThrows(DefinitionValidationException.class,
                () -> activate(failingService, lifecycleRequest(created.getId(), "operation-activate-001")));

        assertEquals(AttachmentConfigStatusEnum.DRAFT,
                service.getDefinition(created.getId()).getAttachmentTemplates().get(0).getConfigStatus());
        assertEquals(ActivationStatusEnum.INACTIVE.name(),
                definitionRepository.findById(created.getId()).getActivationStatus());
        assertEquals(0L, countRowsByOperationId("operation-activate-001"));
        assertEquals(0, processDefinitionCache.definitionIds.size());
    }

    @Test
    void activatedDefinitionStartsInstanceWithFrozenActiveAttachmentGroup() {
        ProcessDefinitionDTO created = service.createDefinition(createRequest("operation-001", "deposit"));
        insertAttachmentTemplate();
        saveGraph(created.getId(), depositGraph("operation-save-001"));
        publish(lifecycleRequest(created.getId(), "operation-publish-001"));
        activate(lifecycleRequest(created.getId(), "operation-activate-001"));

        ProcessInstanceRepository runtimeInstanceRepository = mock(ProcessInstanceRepository.class);
        RuntimeDefinitionLoader runtimeDefinitionLoader = new RuntimeDefinitionLoader(definitionRepository, service,
                new ProcessDefinitionCache());
        RuntimeRequestValidator runtimeRequestValidator = mock(RuntimeRequestValidator.class);
        RuntimeOperationExecutor runtimeOperationExecutor = mock(RuntimeOperationExecutor.class);
        DefaultProcessRuntimeService runtimeService = new DefaultProcessRuntimeService(runtimeInstanceRepository,
                mock(ActiveTaskRepository.class), mock(HistoryTaskRepository.class), runtimeDefinitionLoader,
                runtimeRequestValidator, runtimeOperationExecutor, mock(RuntimeNodeAdvancer.class),
                mock(AttachmentService.class), mock(CallbackService.class));
        StartProcessRequest startRequest = new StartProcessRequest();
        startRequest.setOperationId("operation-start-001");
        startRequest.setProcessCode("deposit");
        startRequest.setInstanceTitle("Deposit request");
        startRequest.setStarterUserId("starter-001");
        startRequest.setStarterDeptId("dept-001");
        startRequest.setVariables(Collections.<String, Object>emptyMap());
        UserContext starter = new UserContext("starter-001", "Starter", "dept-001", "Department");
        when(runtimeRequestValidator.validateStart(startRequest)).thenReturn(starter);
        when(runtimeOperationExecutor.begin(eq(startRequest), eq("START"),
                eq("starter-001"), isNull(), isNull(), any(LocalDateTime.class)))
                .thenReturn(new OperationIdempotencyDecision(OperationIdempotencyDecisionType.NEW, null));
        when(runtimeInstanceRepository.insert(any(ProcessInstanceEntity.class))).thenReturn(1);

        runtimeService.startProcess(startRequest);

        ArgumentCaptor<ProcessInstanceEntity> instanceCaptor = ArgumentCaptor.forClass(ProcessInstanceEntity.class);
        verify(runtimeInstanceRepository).insert(instanceCaptor.capture());
        assertEquals("attachment-group-001", instanceCaptor.getValue().getAttachmentConfigId());
    }

    @Test
    void archiveInactivePublishedDefinitionAndRejectFurtherActivation() {
        ProcessDefinitionDTO created = service.createDefinition(createRequest("operation-001", "deposit"));
        saveGraph(created.getId(), simpleLinearGraph("operation-save-001"));
        publish(lifecycleRequest(created.getId(), "operation-publish-001"));
        processDefinitionCache.clear();

        ProcessDefinitionDTO archived = archive(lifecycleRequest(created.getId(), "operation-archive-001"));

        ProcessDefinitionEntity persisted = definitionRepository.findById(created.getId());
        assertEquals(DefinitionStatusEnum.ARCHIVED, archived.getDefinitionStatus());
        assertEquals(ActivationStatusEnum.INACTIVE, archived.getActivationStatus());
        assertEquals("operator-lifecycle", persisted.getArchivedBy());
        assertNotNull(persisted.getArchivedAt());
        assertThrows(IllegalStateException.class,
                () -> activate(lifecycleRequest(created.getId(), "operation-activate-001")));
        assertEquals(1L, countAuditRows("operation-archive-001", "DEFINITION_ARCHIVE"));
        assertEquals(Collections.singletonList(created.getId()), processDefinitionCache.definitionIds);
    }

    @Test
    void grayReleaseMethodsRemainExplicitlyUnsupportedInM3() {
        ProcessDefinitionDTO created = service.createDefinition(createRequest("operation-001", "deposit"));
        GrayReleaseRequest enable = new GrayReleaseRequest();
        enable.setDefinitionId(created.getId());
        enable.setOperationId("operation-gray-001");
        enable.setOperatorUserId("operator-lifecycle");
        enable.setGrayRuleConfig(Collections.<String, Object>singletonMap("ratio", Integer.valueOf(10)));

        assertThrows(UnsupportedOperationException.class, () -> service.enableGray(enable));
        assertThrows(UnsupportedOperationException.class,
                () -> service.disableGray(lifecycleRequest(created.getId(), "operation-gray-002")));
    }

    @Test
    void saveGraphRollsBackOldGraphWhenEdgeInsertFails() {
        ProcessDefinitionDTO created = service.createDefinition(createRequest("operation-001", "deposit"));
        saveGraph(created.getId(), simpleLinearGraph("operation-save-001"));
        processDefinitionCache.clear();

        assertThrows(RuntimeException.class,
                () -> saveGraph(created.getId(), duplicateDefaultEdgeGraph("operation-save-002")));

        ProcessDefinitionDetailDTO detail = service.getDefinition(created.getId());
        assertEquals(Arrays.asList("start", "review", "end"), nodeCodes(detail));
        assertEquals(Arrays.asList("edge-start-review", "edge-review-end"), edgeCodes(detail));
        assertEquals(0, processDefinitionCache.definitionIds.size());
    }

    @Test
    void saveGraphRollsBackGraphWhenAttachmentConfigInsertFails() {
        ProcessDefinitionDTO created = service.createDefinition(createRequest("operation-001", "deposit"));
        saveGraph(created.getId(), simpleLinearGraph("operation-save-001"));
        processDefinitionCache.clear();

        assertThrows(RuntimeException.class,
                () -> saveGraph(created.getId(), graphWithMissingAttachmentTemplate("operation-save-002")));

        ProcessDefinitionDetailDTO detail = service.getDefinition(created.getId());
        assertEquals(Arrays.asList("start", "review", "end"), nodeCodes(detail));
        assertEquals(Arrays.asList("edge-start-review", "edge-review-end"), edgeCodes(detail));
        assertEquals(0, processDefinitionCache.definitionIds.size());
    }

    @Test
    void saveGraphRollsBackGraphWhenFormFieldInsertFails() {
        ProcessDefinitionDTO created = service.createDefinition(createRequest("operation-001", "deposit"));
        saveGraph(created.getId(), simpleLinearGraph("operation-save-001"));
        processDefinitionCache.clear();

        assertThrows(RuntimeException.class,
                () -> saveGraph(created.getId(), graphWithDuplicateFormFields("operation-save-002")));

        ProcessDefinitionDetailDTO detail = service.getDefinition(created.getId());
        assertEquals(Arrays.asList("start", "review", "end"), nodeCodes(detail));
        assertEquals(Arrays.asList("edge-start-review", "edge-review-end"), edgeCodes(detail));
        assertEquals(0, processDefinitionCache.definitionIds.size());
    }

    @Test
    void saveGraphRejectsNonEditableDefinition() {
        ProcessDefinitionDTO created = service.createDefinition(createRequest("operation-001", "deposit"));
        jdbcTemplate.update("UPDATE process_definition SET definition_status = 'PUBLISHED' WHERE id = ?",
                created.getId());

        assertThrows(IllegalStateException.class,
                () -> saveGraph(created.getId(), simpleLinearGraph("operation-save-001")));
        assertEquals(0, processDefinitionCache.definitionIds.size());
    }

    @Test
    void saveGraphReplaysSameOperationWithoutReplacingRowsAgain() {
        ProcessDefinitionDTO created = service.createDefinition(createRequest("operation-001", "deposit"));
        SaveProcessGraphRequest request = simpleLinearGraph("operation-save-001");

        saveGraph(created.getId(), request);
        String firstNodeId = service.getDefinition(created.getId()).getNodes().get(0).getId();
        processDefinitionCache.clear();
        saveGraph(created.getId(), request);

        ProcessDefinitionDetailDTO detail = service.getDefinition(created.getId());
        assertEquals(firstNodeId, detail.getNodes().get(0).getId());
        assertEquals(3, detail.getNodes().size());
        assertEquals(2, detail.getEdges().size());
        assertEquals(0, processDefinitionCache.definitionIds.size());
    }

    @Test
    void copyDefinitionCreatesNextDraftAndCopiesGraphExtensionsWithFreshIds() {
        ProcessDefinitionDTO source = service.createDefinition(createRequest("operation-001", "deposit"));
        insertAttachmentTemplate();
        saveGraph(source.getId(), depositGraph("operation-save-001"));
        ProcessDefinitionDetailDTO sourceDetail = service.getDefinition(source.getId());
        jdbcTemplate.update("UPDATE process_definition SET definition_status = 'PUBLISHED', "
                        + "activation_status = 'ACTIVE', gray_status = 'ON', gray_rule_config = ?, "
                        + "archived_by = ?, archived_at = ? WHERE id = ?",
                "{\"ratio\":10}", "archiver", "2026-07-17 12:00:00", source.getId());
        processDefinitionCache.clear();

        CopyProcessDefinitionRequest request = copyRequest("operation-copy-001");
        request.setProcessCode("ignored_new_code");
        request.setProcessName("Deposit Copy");

        ProcessDefinitionDTO copied = copyDefinition(source.getId(), request);
        ProcessDefinitionEntity copiedEntity = definitionRepository.findById(copied.getId());
        ProcessDefinitionDetailDTO copiedDetail = service.getDefinition(copied.getId());

        assertNotEquals(source.getId(), copied.getId());
        assertEquals("deposit", copied.getProcessCode());
        assertEquals(Integer.valueOf(2), copied.getVersion());
        assertEquals("Deposit Copy", copied.getProcessName());
        assertEquals(DefinitionStatusEnum.DRAFT, copied.getDefinitionStatus());
        assertEquals(ActivationStatusEnum.INACTIVE, copied.getActivationStatus());
        assertEquals(GrayStatusEnum.OFF, copied.getGrayStatus());
        assertNull(copiedEntity.getGrayRuleConfig());
        assertNull(copiedEntity.getArchivedBy());
        assertNull(copiedEntity.getArchivedAt());
        assertEquals("operator-copy", copiedEntity.getCreatedBy());
        assertEquals("operator-copy", copiedEntity.getUpdatedBy());

        assertEquals(nodeCodes(sourceDetail), nodeCodes(copiedDetail));
        assertDifferentNodeIds(sourceDetail, copiedDetail);
        assertEquals(edgeCodes(sourceDetail), edgeCodes(copiedDetail));
        assertDifferentEdgeIds(sourceDetail, copiedDetail);
        assertEquals(formFieldCodes(sourceDetail), formFieldCodes(copiedDetail));
        assertNotEquals(sourceDetail.getFormFields().get(0).getId(),
                copiedDetail.getFormFields().get(0).getId());
        assertEquals(attachmentCodes(sourceDetail), attachmentCodes(copiedDetail));
        ProcessAttachmentTemplateDTO sourceAttachment = sourceDetail.getAttachmentTemplates().get(0);
        ProcessAttachmentTemplateDTO copiedAttachment = copiedDetail.getAttachmentTemplates().get(0);
        assertNotEquals(sourceAttachment.getId(), copiedAttachment.getId());
        assertNotEquals(sourceAttachment.getAttachmentConfigId(), copiedAttachment.getAttachmentConfigId());
        assertEquals(AttachmentConfigStatusEnum.DRAFT, copiedAttachment.getConfigStatus());
        assertNull(copiedAttachment.getActivatedAt());
        assertEquals(sourceAttachment.getCreatedBy(), copiedAttachment.getCreatedBy());
        assertEquals(Collections.singletonList(copied.getId()), processDefinitionCache.definitionIds);
    }

    @Test
    void copyDefinitionReplaysSameOperationWithoutCreatingAnotherVersion() {
        ProcessDefinitionDTO source = service.createDefinition(createRequest("operation-001", "deposit"));
        saveGraph(source.getId(), simpleLinearGraph("operation-save-001"));

        ProcessDefinitionDTO copied = copyDefinition(source.getId(), copyRequest("operation-copy-001"));
        processDefinitionCache.clear();
        CopyProcessDefinitionRequest replayRequest = copyRequest("operation-copy-001");
        replayRequest.setProcessCode("ignored_on_replay");

        ProcessDefinitionDTO replay = copyDefinition(source.getId(), replayRequest);

        assertEquals(copied.getId(), replay.getId());
        assertEquals(Integer.valueOf(2), definitionRepository.findMaxVersionByProcessCode("deposit"));
        assertEquals(0, processDefinitionCache.definitionIds.size());
    }

    @Test
    void copyDefinitionRollsBackTargetWhenAttachmentConfigCopyFails() {
        ProcessDefinitionDTO source = service.createDefinition(createRequest("operation-001", "deposit"));
        insertAttachmentTemplate();
        saveGraph(source.getId(), depositGraph("operation-save-001"));
        processDefinitionCache.clear();
        DefaultProcessDefinitionService failingService = new DefaultProcessDefinitionService(definitionRepository,
                nodeRepository,
                edgeRepository,
                formFieldManager,
                new FailingAttachmentConfigManager(attachmentConfigRepository,
                        attachmentTemplateRepository,
                        new ProcessDefinitionAttachmentConfigValidator(attachmentTemplateRepository)),
                new ProcessOperationRecordRepository(jdbcTemplate),
                processDefinitionCache);

        assertThrows(RuntimeException.class,
                () -> copyDefinition(failingService, source.getId(), copyRequest("operation-copy-001")));

        assertEquals(Integer.valueOf(1), definitionRepository.findMaxVersionByProcessCode("deposit"));
        assertEquals(0, processDefinitionCache.definitionIds.size());
    }

    @Test
    void copyDefinitionRetriesVersionConflictAndUsesNextAvailableVersion() {
        ProcessDefinitionDTO source = service.createDefinition(createRequest("operation-001", "deposit"));
        saveGraph(source.getId(), simpleLinearGraph("operation-save-001"));
        ProcessDefinitionRepository conflictingRepository =
                new VersionConflictOnceDefinitionRepository(jdbcTemplate);
        DefaultProcessDefinitionService conflictingService = new DefaultProcessDefinitionService(
                conflictingRepository,
                nodeRepository,
                edgeRepository,
                formFieldManager,
                attachmentConfigManager,
                new ProcessOperationRecordRepository(jdbcTemplate),
                processDefinitionCache);

        ProcessDefinitionDTO copied = copyDefinition(conflictingService,
                source.getId(), copyRequest("operation-copy-001"));

        assertEquals(Integer.valueOf(3), copied.getVersion());
        assertEquals(Integer.valueOf(3), definitionRepository.findMaxVersionByProcessCode("deposit"));
    }

    @Test
    void deleteDefinitionExplicitlyDeletesDefinitionRuntimeChildrenAndPreservesLogs() {
        ProcessDefinitionDTO created = service.createDefinition(createRequest("operation-001", "deposit"));
        insertAttachmentTemplate();
        saveGraph(created.getId(), depositGraph("operation-save-001"));
        insertRuntimeData(created.getId());
        jdbcTemplate.update("UPDATE process_definition SET definition_status = 'ARCHIVED' WHERE id = ?",
                created.getId());
        processDefinitionCache.clear();

        OperationResult result = deleteDefinition(deleteRequest(created.getId(), "operation-delete-001"));

        assertEquals("operation-delete-001", result.getOperationId());
        assertEquals(OperationTargetTypeEnum.DEFINITION, result.getTargetType());
        assertEquals(created.getId(), result.getTargetId());
        assertTrue(result.isDeleted());
        assertEquals(false, result.isReplayed());
        assertNull(definitionRepository.findById(created.getId()));
        assertEquals(0L, countRows("process_form_field"));
        assertEquals(0L, countRows("process_definition_attachment_config"));
        assertEquals(0L, countRows("process_attachment"));
        assertEquals(0L, countRows("process_read_record"));
        assertEquals(0L, countRows("process_history_task"));
        assertEquals(0L, countRows("process_active_task"));
        assertEquals(0L, countRows("process_task_group"));
        assertEquals(0L, countRows("process_reminder_record"));
        assertEquals(0L, countRows("process_alert_record"));
        assertEquals(0L, countRows("process_instance"));
        assertEquals(0L, countRows("process_edge"));
        assertEquals(0L, countRows("process_node"));
        assertEquals(1L, countRows("process_audit_log"));
        assertEquals(1L, countRows("process_callback_log"));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT instance_id FROM process_audit_log WHERE id = ?", String.class, "audit-001"));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT instance_id FROM process_callback_log WHERE id = ?", String.class, "callback-001"));
        assertEquals(1L, countRowsByOperationId("operation-delete-001"));
        assertEquals(Collections.singletonList(created.getId()), processDefinitionCache.definitionIds);
    }

    @Test
    void deleteDefinitionReplaysSameOperationWithoutDeletingAgainOrInvalidatingCache() {
        ProcessDefinitionDTO created = service.createDefinition(createRequest("operation-001", "deposit"));
        saveGraph(created.getId(), simpleLinearGraph("operation-save-001"));
        OperationResult first = deleteDefinition(deleteRequest(created.getId(), "operation-delete-001"));
        processDefinitionCache.clear();

        OperationResult replay = deleteDefinition(deleteRequest(created.getId(), "operation-delete-001"));

        assertEquals(first.getTargetId(), replay.getTargetId());
        assertTrue(replay.isDeleted());
        assertTrue(replay.isReplayed());
        assertEquals(1L, countRowsByOperationId("operation-delete-001"));
        assertEquals(0, processDefinitionCache.definitionIds.size());
    }

    @Test
    void deleteDefinitionRejectsActiveDefinition() {
        ProcessDefinitionDTO created = service.createDefinition(createRequest("operation-001", "deposit"));
        jdbcTemplate.update("UPDATE process_definition SET activation_status = 'ACTIVE' WHERE id = ?",
                created.getId());

        assertThrows(IllegalStateException.class,
                () -> deleteDefinition(deleteRequest(created.getId(), "operation-delete-001")));

        assertNotNull(definitionRepository.findById(created.getId()));
        assertEquals(0L, countRowsByOperationId("operation-delete-001"));
        assertEquals(0, processDefinitionCache.definitionIds.size());
    }

    @Test
    void deleteDefinitionRollsBackWhenExtensionDeleteFails() {
        ProcessDefinitionDTO created = service.createDefinition(createRequest("operation-001", "deposit"));
        insertAttachmentTemplate();
        saveGraph(created.getId(), depositGraph("operation-save-001"));
        insertRuntimeData(created.getId());
        processDefinitionCache.clear();
        DefaultProcessDefinitionService failingService = new DefaultProcessDefinitionService(definitionRepository,
                nodeRepository,
                edgeRepository,
                new FailingFormFieldManager(formFieldRepository, new ProcessFormFieldValidator()),
                attachmentConfigManager,
                new ProcessOperationRecordRepository(jdbcTemplate),
                processDefinitionCache);

        assertThrows(RuntimeException.class,
                () -> deleteDefinition(failingService,
                        deleteRequest(created.getId(), "operation-delete-001")));

        assertNotNull(definitionRepository.findById(created.getId()));
        assertEquals(1L, countRows("process_form_field"));
        assertEquals(1L, countRows("process_definition_attachment_config"));
        assertEquals(1L, countRows("process_instance"));
        assertEquals(1L, countRows("process_active_task"));
        assertEquals(0L, countRowsByOperationId("operation-delete-001"));
        assertEquals(0, processDefinitionCache.definitionIds.size());
    }

    @Test
    void listNodesReturnsOnlySortedNodes() {
        ProcessDefinitionDTO created = service.createDefinition(createRequest("operation-001", "deposit"));
        nodeRepository.batchInsert(Arrays.asList(
                node("node-b", created.getId(), "b", 10),
                node("node-a", created.getId(), "a", 10)));

        List<ProcessNodeDTO> nodes = service.listNodes(created.getId());

        assertEquals(2, nodes.size());
        assertEquals("a", nodes.get(0).getNodeCode());
        assertEquals("b", nodes.get(1).getNodeCode());
    }

    @Test
    void searchDefinitionsFiltersAndSortsStably() {
        LocalDateTime ten = LocalDateTime.of(2026, 7, 17, 10, 0);
        LocalDateTime eleven = LocalDateTime.of(2026, 7, 17, 11, 0);
        definitionRepository.insert(definition("definition-001", "deposit", "Deposit A", 1, ten));
        definitionRepository.insert(definition("definition-002", "deposit", "Deposit B", 2, eleven));
        definitionRepository.insert(definition("definition-003", "expense", "Expense", 1, eleven));

        ProcessDefinitionQuery query = new ProcessDefinitionQuery();
        query.setSystemCode("fund");
        query.setDefinitionStatus(DefinitionStatusEnum.DRAFT);
        query.setActivationStatus(ActivationStatusEnum.INACTIVE);
        PageResult<ProcessDefinitionDTO> result = service.searchDefinitions(query);

        assertEquals(Integer.valueOf(DefinitionRequestValidator.DEFAULT_PAGE_NO), result.getPageNo());
        assertEquals(Integer.valueOf(DefinitionRequestValidator.DEFAULT_PAGE_SIZE), result.getPageSize());
        assertEquals(Long.valueOf(3L), result.getTotal());
        assertEquals("definition-002", result.getRecords().get(0).getId());
        assertEquals("definition-003", result.getRecords().get(1).getId());
        assertEquals("definition-001", result.getRecords().get(2).getId());

        ProcessDefinitionQuery filtered = new ProcessDefinitionQuery();
        filtered.setProcessCode("deposit");
        PageResult<ProcessDefinitionDTO> depositOnly = service.searchDefinitions(filtered);
        assertEquals(Long.valueOf(2L), depositOnly.getTotal());
    }

    @Test
    void searchDefinitionsCapsPageSize() {
        ProcessDefinitionQuery query = new ProcessDefinitionQuery();
        query.setPageSize(Integer.valueOf(1000));

        PageResult<ProcessDefinitionDTO> result = service.searchDefinitions(query);

        assertEquals(Integer.valueOf(DefinitionRequestValidator.MAX_PAGE_SIZE), result.getPageSize());
    }

    private static CreateProcessDefinitionRequest createRequest(String operationId, String processCode) {
        CreateProcessDefinitionRequest request = new CreateProcessDefinitionRequest();
        request.setOperationId(operationId);
        request.setOperatorUserId("operator-001");
        request.setProcessCode(processCode);
        request.setProcessName(processCode + " name");
        request.setSystemCode("fund");
        return request;
    }

    private ProcessDefinitionDTO saveGraph(final String definitionId, final SaveProcessGraphRequest request) {
        return transactionTemplate.execute(status -> service.saveGraph(definitionId, request));
    }

    private ProcessDefinitionDTO copyDefinition(final String definitionId,
                                                final CopyProcessDefinitionRequest request) {
        return copyDefinition(service, definitionId, request);
    }

    private ProcessDefinitionDTO copyDefinition(final DefaultProcessDefinitionService targetService,
                                                final String definitionId,
                                                final CopyProcessDefinitionRequest request) {
        return transactionTemplate.execute(status -> targetService.copyDefinition(definitionId, request));
    }

    private OperationResult deleteDefinition(final DefinitionOperationRequest request) {
        return deleteDefinition(service, request);
    }

    private OperationResult deleteDefinition(final DefaultProcessDefinitionService targetService,
                                             final DefinitionOperationRequest request) {
        return transactionTemplate.execute(status -> targetService.deleteDefinition(request));
    }

    private ProcessDefinitionDTO publish(final DefinitionOperationRequest request) {
        return transactionTemplate.execute(status -> service.publish(request));
    }

    private ProcessDefinitionDTO activate(final DefinitionOperationRequest request) {
        return activate(service, request);
    }

    private ProcessDefinitionDTO activate(final DefaultProcessDefinitionService targetService,
                                          final DefinitionOperationRequest request) {
        return transactionTemplate.execute(status -> targetService.activate(request));
    }

    private ProcessDefinitionDTO deactivate(final DefinitionOperationRequest request) {
        return transactionTemplate.execute(status -> service.deactivate(request));
    }

    private ProcessDefinitionDTO archive(final DefinitionOperationRequest request) {
        return transactionTemplate.execute(status -> service.archive(request));
    }

    private static CopyProcessDefinitionRequest copyRequest(String operationId) {
        CopyProcessDefinitionRequest request = new CopyProcessDefinitionRequest();
        request.setOperationId(operationId);
        request.setOperatorUserId("operator-copy");
        return request;
    }

    private static DefinitionOperationRequest deleteRequest(String definitionId, String operationId) {
        DefinitionOperationRequest request = new DefinitionOperationRequest();
        request.setDefinitionId(definitionId);
        request.setOperationId(operationId);
        request.setOperatorUserId("operator-delete");
        return request;
    }

    private static DefinitionOperationRequest lifecycleRequest(String definitionId, String operationId) {
        DefinitionOperationRequest request = new DefinitionOperationRequest();
        request.setDefinitionId(definitionId);
        request.setOperationId(operationId);
        request.setOperatorUserId("operator-lifecycle");
        return request;
    }

    private static SaveProcessGraphRequest depositGraph(String operationId) {
        SaveProcessGraphRequest request = graphRequest(operationId);
        request.setNodes(Arrays.asList(
                nodeDto("start", "Start", "START", 10),
                userTaskDto("apply", "Apply", "STARTER", null, 20),
                userTaskDto("manager", "Manager", "USER", "{\"userIds\":[\"manager\"]}", 30),
                userTaskDto("finance", "Finance", "USER", "{\"userIds\":[\"finance\"]}", 40),
                nodeDto("end", "End", "END", 50)));
        request.setEdges(Arrays.asList(
                edgeDto("edge-start-apply", "start", "apply", 10),
                edgeDto("edge-apply-manager", "apply", "manager", 20),
                edgeDto("edge-manager-finance", "manager", "finance", 30),
                edgeDto("edge-finance-end", "finance", "end", 40)));
        request.setFormFields(Collections.singletonList(formFieldDto("amount", 10)));
        request.setAttachmentConfigs(Collections.singletonList(attachmentConfigDto("receipt", "template-001", 10)));
        return request;
    }

    private static SaveProcessGraphRequest simpleLinearGraph(String operationId) {
        SaveProcessGraphRequest request = graphRequest(operationId);
        request.setNodes(Arrays.asList(
                nodeDto("start", "Start", "START", 10),
                userTaskDto("review", "Review", "USER", "{\"userIds\":[\"reviewer\"]}", 20),
                nodeDto("end", "End", "END", 30)));
        request.setEdges(Arrays.asList(
                edgeDto("edge-start-review", "start", "review", 10),
                edgeDto("edge-review-end", "review", "end", 20)));
        return request;
    }

    private static SaveProcessGraphRequest duplicateDefaultEdgeGraph(String operationId) {
        SaveProcessGraphRequest request = graphRequest(operationId);
        request.setNodes(Arrays.asList(
                nodeDto("start", "Start", "START", 10),
                userTaskDto("review-a", "Review A", "USER", "{\"userIds\":[\"a\"]}", 20),
                userTaskDto("review-b", "Review B", "USER", "{\"userIds\":[\"b\"]}", 30),
                nodeDto("end", "End", "END", 40)));
        ProcessEdgeDTO toA = edgeDto("edge-start-a", "start", "review-a", 10);
        toA.setDefaultEdge(Boolean.TRUE);
        ProcessEdgeDTO toB = edgeDto("edge-start-b", "start", "review-b", 20);
        toB.setDefaultEdge(Boolean.TRUE);
        request.setEdges(Arrays.asList(
                toA,
                toB,
                edgeDto("edge-a-end", "review-a", "end", 30),
                edgeDto("edge-b-end", "review-b", "end", 40)));
        return request;
    }

    private static SaveProcessGraphRequest graphWithMissingAttachmentTemplate(String operationId) {
        SaveProcessGraphRequest request = simpleLinearGraph(operationId);
        request.setAttachmentConfigs(Collections.singletonList(
                attachmentConfigDto("receipt", "missing-template", 10)));
        return request;
    }

    private static SaveProcessGraphRequest graphWithDuplicateFormFields(String operationId) {
        SaveProcessGraphRequest request = simpleLinearGraph(operationId);
        request.setFormFields(Arrays.asList(
                formFieldDto("amount", 10),
                formFieldDto("amount", 20)));
        return request;
    }

    private static SaveProcessGraphRequest graphRequest(String operationId) {
        SaveProcessGraphRequest request = new SaveProcessGraphRequest();
        request.setOperationId(operationId);
        request.setOperatorUserId("operator-save");
        return request;
    }

    private static ProcessNodeDTO nodeDto(String code, String name, String type, int sortOrder) {
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setNodeCode(code);
        node.setNodeName(name);
        node.setNodeType(NodeTypeEnum.valueOf(type));
        node.setMultiInstanceMode(MultiInstanceModeEnum.SINGLE);
        node.setSortOrder(Integer.valueOf(sortOrder));
        return node;
    }

    private static ProcessNodeDTO userTaskDto(String code,
                                              String name,
                                              String approverRuleType,
                                              String approverRuleConfig,
                                              int sortOrder) {
        ProcessNodeDTO node = nodeDto(code, name, "USER_TASK", sortOrder);
        node.setApproverRuleType(ApproverRuleTypeEnum.valueOf(approverRuleType));
        node.setApproverRuleConfig(approverRuleConfig);
        return node;
    }

    private static ProcessEdgeDTO edgeDto(String code, String sourceNodeCode, String targetNodeCode, int sortOrder) {
        ProcessEdgeDTO edge = new ProcessEdgeDTO();
        edge.setEdgeCode(code);
        edge.setSourceNodeCode(sourceNodeCode);
        edge.setTargetNodeCode(targetNodeCode);
        edge.setDefaultEdge(Boolean.FALSE);
        edge.setSortOrder(Integer.valueOf(sortOrder));
        return edge;
    }

    private static ProcessFormFieldDTO formFieldDto(String fieldCode, int sortOrder) {
        ProcessFormFieldDTO formField = new ProcessFormFieldDTO();
        formField.setFieldCode(fieldCode);
        formField.setFieldName("Amount");
        formField.setFieldType("number");
        formField.setControlType("number");
        formField.setRequired(Boolean.TRUE);
        formField.setSortOrder(Integer.valueOf(sortOrder));
        return formField;
    }

    private static ProcessAttachmentConfigDTO attachmentConfigDto(String attachmentCode,
                                                                  String templateId,
                                                                  int sortOrder) {
        ProcessAttachmentConfigDTO config = new ProcessAttachmentConfigDTO();
        config.setAttachmentConfigId("attachment-group-001");
        config.setAttachmentTemplateId(templateId);
        config.setAttachmentCode(attachmentCode);
        config.setRequired(Boolean.TRUE);
        config.setMinCount(Integer.valueOf(1));
        config.setMaxCount(Integer.valueOf(2));
        config.setApplicableNodeCodes(Collections.singletonList("apply"));
        config.setSortOrder(Integer.valueOf(sortOrder));
        return config;
    }

    private static List<String> nodeCodes(ProcessDefinitionDetailDTO detail) {
        List<String> codes = new ArrayList<String>();
        for (ProcessNodeDTO node : detail.getNodes()) {
            codes.add(node.getNodeCode());
        }
        return codes;
    }

    private static List<String> edgeCodes(ProcessDefinitionDetailDTO detail) {
        List<String> codes = new ArrayList<String>();
        for (ProcessEdgeDTO edge : detail.getEdges()) {
            codes.add(edge.getEdgeCode());
        }
        return codes;
    }

    private static List<String> formFieldCodes(ProcessDefinitionDetailDTO detail) {
        List<String> codes = new ArrayList<String>();
        for (ProcessFormFieldDTO formField : detail.getFormFields()) {
            codes.add(formField.getFieldCode());
        }
        return codes;
    }

    private static List<String> attachmentCodes(ProcessDefinitionDetailDTO detail) {
        List<String> codes = new ArrayList<String>();
        for (ProcessAttachmentTemplateDTO attachment : detail.getAttachmentTemplates()) {
            codes.add(attachment.getAttachmentCode());
        }
        return codes;
    }

    private static boolean hasIssue(ValidationResult result,
                                    String code,
                                    String nodeCode,
                                    String edgeCode) {
        for (ValidationResult.Issue issue : result.getIssues()) {
            if (code.equals(issue.getCode())
                    && nodeCode.equals(issue.getNodeCode())
                    && edgeCode.equals(issue.getEdgeCode())) {
                return true;
            }
        }
        return false;
    }

    private static void assertDifferentNodeIds(ProcessDefinitionDetailDTO source,
                                               ProcessDefinitionDetailDTO copied) {
        for (int i = 0; i < source.getNodes().size(); i++) {
            assertNotEquals(source.getNodes().get(i).getId(), copied.getNodes().get(i).getId());
        }
    }

    private static void assertDifferentEdgeIds(ProcessDefinitionDetailDTO source,
                                               ProcessDefinitionDetailDTO copied) {
        for (int i = 0; i < source.getEdges().size(); i++) {
            assertNotEquals(source.getEdges().get(i).getId(), copied.getEdges().get(i).getId());
        }
    }

    private static ProcessDefinitionEntity definition(String id,
                                                      String processCode,
                                                      String processName,
                                                      int version,
                                                      LocalDateTime updatedAt) {
        ProcessDefinitionEntity entity = new ProcessDefinitionEntity();
        entity.setId(id);
        entity.setProcessCode(processCode);
        entity.setProcessName(processName);
        entity.setSystemCode("fund");
        entity.setVersion(Integer.valueOf(version));
        entity.setDefinitionStatus("DRAFT");
        entity.setActivationStatus("INACTIVE");
        entity.setGrayStatus("OFF");
        entity.setCreatedBy("operator-001");
        entity.setCreatedAt(updatedAt);
        entity.setUpdatedBy("operator-001");
        entity.setUpdatedAt(updatedAt);
        return entity;
    }

    private static ProcessNodeEntity node(String id, String definitionId, String nodeCode, int sortOrder) {
        ProcessNodeEntity entity = new ProcessNodeEntity();
        entity.setId(id);
        entity.setDefinitionId(definitionId);
        entity.setNodeCode(nodeCode);
        entity.setNodeName(nodeCode);
        entity.setNodeType("USER_TASK");
        entity.setSortOrder(Integer.valueOf(sortOrder));
        return entity;
    }

    private static ProcessEdgeEntity edge(String id,
                                          String definitionId,
                                          String sourceNodeCode,
                                          String targetNodeCode,
                                          int sortOrder) {
        ProcessEdgeEntity entity = new ProcessEdgeEntity();
        entity.setId(id);
        entity.setDefinitionId(definitionId);
        entity.setEdgeCode(id);
        entity.setSourceNodeCode(sourceNodeCode);
        entity.setTargetNodeCode(targetNodeCode);
        entity.setDefaultEdge(Boolean.FALSE);
        entity.setSortOrder(Integer.valueOf(sortOrder));
        return entity;
    }

    private void insertFormField(String definitionId) {
        jdbcTemplate.update("INSERT INTO process_form_field "
                        + "(id, definition_id, field_code, field_name, field_type, control_type, required, sort_order) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "field-001", definitionId, "amount", "入金金额", "number", "number", Integer.valueOf(1),
                Integer.valueOf(10));
    }

    private void insertAttachmentTemplate() {
        jdbcTemplate.update("INSERT INTO process_attachment_template "
                        + "(id, attachment_code, template_version, attachment_name, allowed_extensions, "
                        + "max_size_bytes, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "template-001", "receipt", Integer.valueOf(1), "银行回单", "[\"pdf\"]",
                Long.valueOf(1024L), "operator-001", "operator-001");
    }

    private void insertAttachmentConfig(String definitionId) {
        jdbcTemplate.update("INSERT INTO process_definition_attachment_config "
                        + "(id, attachment_config_id, definition_id, attachment_template_id, attachment_code, "
                        + "required, min_count, max_count, applicable_node_codes, sort_order, created_by, updated_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "attachment-config-001", "attachment-group-001", definitionId, "template-001", "receipt",
                Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(2), "[\"apply\"]", Integer.valueOf(10),
                "operator-001", "operator-001");
    }

    private void insertRuntimeData(String definitionId) {
        jdbcTemplate.update("INSERT INTO process_instance "
                        + "(id, definition_id, attachment_config_id, process_code, process_name, version, "
                        + "instance_title, starter_user_id, starter_user_name, current_node_codes, instance_status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "instance-001", definitionId, "attachment-group-001", "deposit", "Deposit",
                Integer.valueOf(1), "Deposit Instance", "starter", "Starter", "review", "RUNNING");
        jdbcTemplate.update("INSERT INTO process_task_group "
                        + "(id, instance_id, node_code, group_type, total_count, completed_count, group_status, "
                        + "lock_version) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "task-group-001", "instance-001", "review", "OR_SIGN", Integer.valueOf(1),
                Integer.valueOf(0), "ACTIVE", Integer.valueOf(0));
        jdbcTemplate.update("INSERT INTO process_active_task "
                        + "(id, instance_id, definition_id, node_code, candidate_user_ids, task_status, "
                        + "task_group_id, lock_version) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "active-task-001", "instance-001", definitionId, "review", "reviewer",
                "ACTIVE", "task-group-001", Integer.valueOf(0));
        jdbcTemplate.update("INSERT INTO process_history_task "
                        + "(id, instance_id, operation_id, active_task_id, node_code, task_group_id, "
                        + "handle_type, action_type) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "history-task-001", "instance-001", "runtime-operation-001", "active-task-001",
                "review", "task-group-001", "NORMAL", "APPROVE");
        jdbcTemplate.update("INSERT INTO process_read_record "
                        + "(id, instance_id, user_id, user_name) VALUES (?, ?, ?, ?)",
                "read-001", "instance-001", "reader", "Reader");
        jdbcTemplate.update("INSERT INTO process_attachment "
                        + "(id, instance_id, owner_type, attachment_code, file_name, size_bytes, storage_key, "
                        + "uploaded_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "attachment-001", "instance-001", "INSTANCE", "receipt", "receipt.pdf",
                Long.valueOf(100L), "storage-key-001", "starter");
        jdbcTemplate.update("INSERT INTO process_reminder_record "
                        + "(id, instance_id, task_id, reminder_type, target_user_ids, message, "
                        + "reminder_status, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "reminder-001", "instance-001", "active-task-001", "MANUAL", "reviewer",
                "please review", "PENDING", "starter");
        jdbcTemplate.update("INSERT INTO process_alert_record "
                        + "(id, instance_id, task_id, alert_type, severity, alert_status, detail_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                "alert-001", "instance-001", "active-task-001", "TASK_TIMEOUT",
                "LOW", "OPEN", "{}");
        jdbcTemplate.update("INSERT INTO process_audit_log "
                        + "(id, instance_id, operation_id, target_type, target_id, action_type, operator_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                "audit-001", "instance-001", "runtime-operation-001", "INSTANCE", "instance-001",
                "START", "starter");
        jdbcTemplate.update("INSERT INTO process_callback_log "
                        + "(id, event_id, instance_id, operation_id, event_type, action_type, payload_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                "callback-001", "event-001", "instance-001", "runtime-operation-001",
                "INSTANCE_STARTED", "START", "{}");
    }

    private long countRows(String tableName) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
        return count == null ? 0L : count.longValue();
    }

    private long countRowsByOperationId(String operationId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM process_operation_record WHERE operation_id = ?",
                Long.class, operationId);
        return count == null ? 0L : count.longValue();
    }

    private long countAuditRows(String operationId, String actionType) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM process_audit_log WHERE operation_id = ? AND action_type = ?",
                Long.class, operationId, actionType);
        return count == null ? 0L : count.longValue();
    }

    private static final class RecordingProcessDefinitionCache extends ProcessDefinitionCache {
        private final List<String> definitionIds = new ArrayList<String>();

        @Override
        public void invalidate(String definitionId) {
            super.invalidate(definitionId);
            definitionIds.add(definitionId);
        }

        private void clear() {
            definitionIds.clear();
        }
    }

    private static final class FailingAttachmentConfigManager
            extends ProcessDefinitionAttachmentConfigManager {

        private FailingAttachmentConfigManager(ProcessDefinitionAttachmentConfigRepository attachmentConfigRepository,
                                               ProcessAttachmentTemplateRepository attachmentTemplateRepository,
                                               ProcessDefinitionAttachmentConfigValidator validator) {
            super(attachmentConfigRepository, attachmentTemplateRepository, validator);
        }

        @Override
        public int copyAttachmentConfigs(String sourceDefinitionId, String targetDefinitionId) {
            throw new RuntimeException("copy attachment config failure");
        }
    }

    private static final class FailingFormFieldManager extends ProcessFormFieldDefinitionManager {

        private FailingFormFieldManager(ProcessFormFieldRepository formFieldRepository,
                                        ProcessFormFieldValidator validator) {
            super(formFieldRepository, validator);
        }

        @Override
        public int deleteFormFields(String definitionId) {
            throw new RuntimeException("delete form field failure");
        }
    }

    private static final class FailingActivationDefinitionRepository extends ProcessDefinitionRepository {

        private FailingActivationDefinitionRepository(JdbcTemplate jdbcTemplate) {
            super(jdbcTemplate);
        }

        @Override
        public int activateFull(String id, String updatedBy) {
            return 0;
        }
    }

    private static final class NoRowsActivatedAttachmentConfigRepository
            extends ProcessDefinitionAttachmentConfigRepository {

        private NoRowsActivatedAttachmentConfigRepository(JdbcTemplate jdbcTemplate) {
            super(jdbcTemplate);
        }

        @Override
        public int activateGroup(String definitionId, String attachmentConfigId, String updatedBy) {
            return 0;
        }
    }

    private static final class VersionConflictOnceDefinitionRepository
            extends ProcessDefinitionRepository {

        private boolean conflictInjected;

        private VersionConflictOnceDefinitionRepository(JdbcTemplate jdbcTemplate) {
            super(jdbcTemplate);
        }

        @Override
        public int insert(ProcessDefinitionEntity entity) {
            if (!conflictInjected
                    && "deposit".equals(entity.getProcessCode())
                    && Integer.valueOf(2).equals(entity.getVersion())) {
                conflictInjected = true;
                ProcessDefinitionEntity concurrent = definition("definition-concurrent",
                        entity.getProcessCode(),
                        "Concurrent Copy",
                        entity.getVersion().intValue(),
                        LocalDateTime.of(2026, 7, 17, 12, 0));
                super.insert(concurrent);
            }
            return super.insert(entity);
        }
    }

    private static void executeSchema(Connection connection) throws IOException, SQLException {
        String sql;
        try (InputStream input = DefaultProcessDefinitionServiceTest.class.getResourceAsStream(SCHEMA)) {
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

package com.flowmind.platform.starter;

import com.flowmind.platform.api.dto.AlertDTO;
import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessCommentDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.ReadRecordDTO;
import com.flowmind.platform.api.dto.ReminderDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.dto.AlertQuery;
import com.flowmind.platform.api.dto.CompletedTaskQuery;
import com.flowmind.platform.api.dto.ReadRecordQuery;
import com.flowmind.platform.api.dto.ReminderQuery;
import com.flowmind.platform.api.dto.StartedInstanceQuery;
import com.flowmind.platform.api.dto.TodoTaskQuery;
import com.flowmind.platform.api.request.HandleAlertRequest;
import com.flowmind.platform.api.request.RemindTaskRequest;
import com.flowmind.platform.api.request.TimeoutScanRequest;
import com.flowmind.platform.api.service.AttachmentService;
import com.flowmind.platform.api.service.CallbackService;
import com.flowmind.platform.api.service.AdminProcessService;
import com.flowmind.platform.api.service.ProcessMonitorService;
import com.flowmind.platform.api.service.ProcessDefinitionService;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import com.flowmind.platform.api.service.TaskQueryService;
import com.flowmind.platform.api.spi.AttachmentAccessProvider;
import com.flowmind.platform.api.spi.ApproverResolver;
import com.flowmind.platform.api.spi.CurrentUserProvider;
import com.flowmind.platform.api.spi.ConditionExpressionEvaluator;
import com.flowmind.platform.api.spi.DelegateProvider;
import com.flowmind.platform.api.spi.FileStorageProvider;
import com.flowmind.platform.api.spi.MessagePublisher;
import com.flowmind.platform.api.spi.OrganizationProvider;
import com.flowmind.platform.api.spi.WorkflowCallbackHandler;
import com.flowmind.platform.core.query.DefaultTaskQueryService;
import com.flowmind.platform.core.query.ProcessTraceAssembler;
import com.flowmind.platform.core.query.ReadRecordManager;
import com.flowmind.platform.core.query.RuntimeQueryAssembler;
import com.flowmind.platform.core.audit.AuditLogWriter;
import com.flowmind.platform.core.audit.DefaultAuditLogWriter;
import com.flowmind.platform.core.callback.CallbackDispatchService;
import com.flowmind.platform.core.callback.CallbackDispatchScheduler;
import com.flowmind.platform.core.callback.CallbackFailureAlertService;
import com.flowmind.platform.core.callback.CallbackLogMapper;
import com.flowmind.platform.core.callback.CallbackOutboxService;
import com.flowmind.platform.core.callback.DefaultCallbackService;
import com.flowmind.platform.core.definition.OperationIdempotencyService;
import com.flowmind.platform.core.definition.DefaultProcessDefinitionService;
import com.flowmind.platform.core.definition.ProcessDefinitionAttachmentConfigManager;
import com.flowmind.platform.core.definition.ProcessDefinitionCache;
import com.flowmind.platform.core.definition.ProcessFormFieldDefinitionManager;
import com.flowmind.platform.core.monitor.ActionExceptionAlertWriter;
import com.flowmind.platform.core.monitor.DefaultProcessMonitorService;
import com.flowmind.platform.core.monitor.MonitorModelMapper;
import com.flowmind.platform.core.monitor.ReminderDeduplicationGuard;
import com.flowmind.platform.core.monitor.ReminderPolicyReader;
import com.flowmind.platform.core.monitor.TimeoutActionExecutor;
import com.flowmind.platform.core.monitor.TimeoutScanScheduler;
import com.flowmind.platform.core.monitor.TimeoutPolicyReader;
import com.flowmind.platform.core.monitor.TimeoutDueDateCalculator;
import com.flowmind.platform.core.runtime.DefaultApproverResolver;
import com.flowmind.platform.core.runtime.ApproverResolveRequestFactory;
import com.flowmind.platform.core.runtime.CountersignTaskCoordinator;
import com.flowmind.platform.core.runtime.DefaultProcessRuntimeService;
import com.flowmind.platform.core.runtime.EnhancedTaskActionCoordinator;
import com.flowmind.platform.core.runtime.InstanceTaskCancellationService;
import com.flowmind.platform.core.runtime.RuntimeDefinitionLoader;
import com.flowmind.platform.core.runtime.RuntimeNodeAdvancer;
import com.flowmind.platform.core.runtime.RuntimeNodeConfigReader;
import com.flowmind.platform.core.runtime.RuntimeStateValidator;
import com.flowmind.platform.core.runtime.SimpleConditionExpressionEvaluator;
import com.flowmind.platform.core.runtime.AdminPermissionGuard;
import com.flowmind.platform.core.runtime.RuntimeOperationExecutor;
import com.flowmind.platform.core.runtime.RuntimeRequestValidator;
import com.flowmind.platform.core.runtime.RuntimeTransactionExecutor;
import com.flowmind.platform.core.runtime.TaskClaimCoordinator;
import com.flowmind.platform.core.task.HistoryTaskWriter;
import com.flowmind.platform.core.validation.ProcessDefinitionAttachmentConfigValidator;
import com.flowmind.platform.core.validation.ProcessFormFieldValidator;
import com.flowmind.platform.core.security.AttachmentAccessGuard;
import com.flowmind.platform.core.attachment.DefaultAttachmentService;
import com.flowmind.platform.mock.InMemoryFileStorageProvider;
import com.flowmind.platform.mock.InMemoryOrganizationProvider;
import com.flowmind.platform.mock.MockAttachmentAccessProvider;
import com.flowmind.platform.mock.MockCurrentUserProvider;
import com.flowmind.platform.mock.RecordingMessagePublisher;
import com.flowmind.platform.mock.RecordingWorkflowCallbackHandler;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessAttachmentRepository;
import com.flowmind.platform.persistence.repository.ProcessAttachmentTemplateRepository;
import com.flowmind.platform.persistence.repository.ProcessAuditLogRepository;
import com.flowmind.platform.persistence.repository.ProcessDefinitionAttachmentConfigRepository;
import com.flowmind.platform.persistence.repository.ProcessHistoryTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import com.flowmind.platform.persistence.repository.ProcessOperationRecordRepository;
import com.flowmind.platform.persistence.repository.ProcessCallbackLogRepository;
import com.flowmind.platform.persistence.repository.ProcessNodeRepository;
import com.flowmind.platform.persistence.repository.ProcessDefinitionRepository;
import com.flowmind.platform.persistence.repository.ProcessEdgeRepository;
import com.flowmind.platform.persistence.repository.ProcessFormFieldRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceDeletionRepository;
import com.flowmind.platform.persistence.repository.HistoryTaskRepository;
import com.flowmind.platform.persistence.repository.TaskGroupRepository;
import com.flowmind.platform.persistence.repository.ProcessReadRecordRepository;
import com.flowmind.platform.persistence.repository.ReminderRecordRepository;
import com.flowmind.platform.persistence.repository.AlertRecordRepository;
import com.flowmind.platform.starter.properties.PlatformProperties;
import org.sqlite.SQLiteDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

@Configuration
@EnableConfigurationProperties(PlatformProperties.class)
@ConditionalOnProperty(prefix = "flow-mind.platform", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PlatformAutoConfiguration {

    /**
     * 当宿主只提供组织架构 SPI 时，复用平台的默认规则解析器接入运行时任务创建链路。
     * 宿主显式提供 {@link ApproverResolver} 时优先使用其实现。
     */
    @Bean
    @ConditionalOnMissingBean
    public DataSource dataSource(PlatformProperties properties) {
        String path = properties.getSqlite().getPath();
        if (path == null || path.trim().isEmpty()) {
            path = "./data/flow-mind.db";
        }
        if (!path.startsWith("jdbc:sqlite:") && !":memory:".equals(path) && !path.startsWith("file:")) {
            File file = new File(path);
            File parent = file.getAbsoluteFile().getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
        }
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl(path.startsWith("jdbc:sqlite:") ? path : "jdbc:sqlite:" + path);
        return dataSource;
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public PlatformSchemaInitializer platformSchemaInitializer(DataSource dataSource) {
        return new PlatformSchemaInitializer(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessHistoryTaskRepository processHistoryTaskRepository(JdbcTemplate jdbcTemplate) {
        return new ProcessHistoryTaskRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public ActiveTaskRepository activeTaskRepository(JdbcTemplate jdbcTemplate) {
        return new ActiveTaskRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessInstanceRepository processInstanceRepository(JdbcTemplate jdbcTemplate) {
        return new ProcessInstanceRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessOperationRecordRepository processOperationRecordRepository(JdbcTemplate jdbcTemplate) {
        return new ProcessOperationRecordRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationIdempotencyService operationIdempotencyService(ProcessOperationRecordRepository operations) {
        return new OperationIdempotencyService(operations);
    }

    @Bean
    @ConditionalOnMissingBean
    public RuntimeOperationExecutor runtimeOperationExecutor(OperationIdempotencyService idempotencyService) {
        return new RuntimeOperationExecutor(idempotencyService);
    }

    @Bean
    @ConditionalOnMissingBean
    public RuntimeRequestValidator runtimeRequestValidator(ObjectProvider<CurrentUserProvider> currentUserProvider) {
        CurrentUserProvider currentUser = currentUserProvider.getIfAvailable();
        if (currentUser == null) {
            currentUser = new RequiredCurrentUserProvider();
        }
        return new RuntimeRequestValidator(currentUser);
    }

    @Bean
    @ConditionalOnMissingBean
    public RuntimeTransactionExecutor runtimeTransactionExecutor(PlatformTransactionManager transactionManager) {
        return new RuntimeTransactionExecutor(transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessAttachmentRepository processAttachmentRepository(JdbcTemplate jdbcTemplate) {
        return new ProcessAttachmentRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessAttachmentTemplateRepository processAttachmentTemplateRepository(JdbcTemplate jdbcTemplate) {
        return new ProcessAttachmentTemplateRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessDefinitionAttachmentConfigRepository processDefinitionAttachmentConfigRepository(JdbcTemplate jdbcTemplate) {
        return new ProcessDefinitionAttachmentConfigRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessAuditLogRepository processAuditLogRepository(JdbcTemplate jdbcTemplate) {
        return new ProcessAuditLogRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessReadRecordRepository processReadRecordRepository(JdbcTemplate jdbcTemplate) {
        return new ProcessReadRecordRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public ReminderRecordRepository reminderRecordRepository(JdbcTemplate jdbcTemplate) {
        return new ReminderRecordRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertRecordRepository alertRecordRepository(JdbcTemplate jdbcTemplate) {
        return new AlertRecordRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessNodeRepository processNodeRepository(JdbcTemplate jdbcTemplate) {
        return new ProcessNodeRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessDefinitionRepository processDefinitionRepository(JdbcTemplate jdbcTemplate) {
        return new ProcessDefinitionRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessEdgeRepository processEdgeRepository(JdbcTemplate jdbcTemplate) {
        return new ProcessEdgeRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessFormFieldRepository processFormFieldRepository(JdbcTemplate jdbcTemplate) {
        return new ProcessFormFieldRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessInstanceDeletionRepository processInstanceDeletionRepository(JdbcTemplate jdbcTemplate) {
        return new ProcessInstanceDeletionRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public HistoryTaskRepository historyTaskRepository(JdbcTemplate jdbcTemplate) {
        return new HistoryTaskRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskGroupRepository taskGroupRepository(JdbcTemplate jdbcTemplate) {
        return new TaskGroupRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessCallbackLogRepository processCallbackLogRepository(JdbcTemplate jdbcTemplate) {
        return new ProcessCallbackLogRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditLogWriter auditLogWriter(ProcessAuditLogRepository auditLogRepository) {
        return new DefaultAuditLogWriter(auditLogRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessTraceAssembler processTraceAssembler() {
        return new ProcessTraceAssembler();
    }

    @Bean
    @ConditionalOnMissingBean
    public RuntimeQueryAssembler runtimeQueryAssembler() {
        return new RuntimeQueryAssembler();
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessDefinitionCache processDefinitionCache() {
        return new ProcessDefinitionCache();
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessFormFieldValidator processFormFieldValidator() {
        return new ProcessFormFieldValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessFormFieldDefinitionManager processFormFieldDefinitionManager(
            ProcessFormFieldRepository repository,
            ProcessFormFieldValidator validator) {
        return new ProcessFormFieldDefinitionManager(repository, validator);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessDefinitionAttachmentConfigValidator processDefinitionAttachmentConfigValidator(
            ProcessAttachmentTemplateRepository templateRepository) {
        return new ProcessDefinitionAttachmentConfigValidator(templateRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessDefinitionAttachmentConfigManager processDefinitionAttachmentConfigManager(
            ProcessDefinitionAttachmentConfigRepository configRepository,
            ProcessAttachmentTemplateRepository templateRepository,
            ProcessDefinitionAttachmentConfigValidator validator) {
        return new ProcessDefinitionAttachmentConfigManager(configRepository, templateRepository, validator);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessDefinitionService processDefinitionService(
            ProcessDefinitionRepository definitionRepository,
            ProcessNodeRepository nodeRepository,
            ProcessEdgeRepository edgeRepository,
            ProcessFormFieldDefinitionManager formFieldManager,
            ProcessDefinitionAttachmentConfigManager attachmentConfigManager,
            OperationIdempotencyService idempotencyService,
            ProcessDefinitionCache definitionCache,
            ObjectProvider<FileStorageProvider> fileStorageProvider) {
        return new DefaultProcessDefinitionService(definitionRepository, nodeRepository, edgeRepository,
                formFieldManager, attachmentConfigManager, idempotencyService, definitionCache,
                fileStorageProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public RuntimeDefinitionLoader runtimeDefinitionLoader(ProcessDefinitionRepository definitionRepository,
                                                           ProcessDefinitionService definitionService,
                                                           ProcessDefinitionCache definitionCache) {
        return new RuntimeDefinitionLoader(definitionRepository, definitionService, definitionCache);
    }

    @Bean
    @ConditionalOnMissingBean
    public RuntimeNodeConfigReader runtimeNodeConfigReader() {
        return new RuntimeNodeConfigReader();
    }

    @Bean
    @ConditionalOnMissingBean
    public ApproverResolveRequestFactory approverResolveRequestFactory(RuntimeNodeConfigReader configReader) {
        return new ApproverResolveRequestFactory(configReader);
    }

    @Bean
    @ConditionalOnMissingBean
    public ConditionExpressionEvaluator conditionExpressionEvaluator() {
        return new SimpleConditionExpressionEvaluator();
    }

    @Bean
    @ConditionalOnMissingBean
    public TimeoutDueDateCalculator timeoutDueDateCalculator(TimeoutPolicyReader timeoutPolicyReader) {
        return new TimeoutDueDateCalculator(timeoutPolicyReader);
    }

    @Bean
    @ConditionalOnMissingBean
    public RuntimeNodeAdvancer runtimeNodeAdvancer(ActiveTaskRepository activeTaskRepository,
                                                   TaskGroupRepository taskGroupRepository,
                                                   ProcessInstanceRepository instanceRepository,
                                                   RuntimeRequestValidator requestValidator,
                                                   ObjectProvider<ApproverResolver> approverResolver,
                                                   ConditionExpressionEvaluator conditionExpressionEvaluator,
                                                   ApproverResolveRequestFactory requestFactory,
                                                   TimeoutDueDateCalculator timeoutDueDateCalculator) {
        ApproverResolver resolver = approverResolver.getIfAvailable();
        if (resolver == null) {
            resolver = request -> {
                throw new IllegalStateException("ApproverResolver bean is required for runtime advancement");
            };
        }
        return new RuntimeNodeAdvancer(activeTaskRepository, taskGroupRepository, instanceRepository,
                requestValidator, resolver, conditionExpressionEvaluator, requestFactory,
                timeoutDueDateCalculator);
    }

    @Bean
    @ConditionalOnMissingBean
    public RuntimeStateValidator runtimeStateValidator(ActiveTaskRepository activeTaskRepository,
                                                       ProcessInstanceRepository instanceRepository,
                                                       TaskGroupRepository taskGroupRepository) {
        return new RuntimeStateValidator(activeTaskRepository, instanceRepository, taskGroupRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public HistoryTaskWriter historyTaskWriter(ProcessHistoryTaskRepository historyTaskRepository) {
        return new HistoryTaskWriter(historyTaskRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public InstanceTaskCancellationService instanceTaskCancellationService(
            ActiveTaskRepository activeTaskRepository,
            TaskGroupRepository taskGroupRepository,
            HistoryTaskWriter historyTaskWriter) {
        return new InstanceTaskCancellationService(activeTaskRepository, taskGroupRepository, historyTaskWriter);
    }

    @Bean
    @ConditionalOnMissingBean
    public EnhancedTaskActionCoordinator enhancedTaskActionCoordinator(
            ProcessInstanceRepository instanceRepository,
            ActiveTaskRepository activeTaskRepository,
            ProcessHistoryTaskRepository historyTaskRepository,
            TaskGroupRepository taskGroupRepository,
            RuntimeDefinitionLoader definitionLoader,
            RuntimeRequestValidator requestValidator,
            RuntimeOperationExecutor operationExecutor,
            RuntimeNodeAdvancer nodeAdvancer,
            RuntimeStateValidator stateValidator,
            HistoryTaskWriter historyTaskWriter,
            RuntimeTransactionExecutor transactionExecutor,
            CallbackService callbackService,
            ObjectProvider<OrganizationProvider> organizationProvider,
            AuditLogWriter auditLogWriter) {
        return new EnhancedTaskActionCoordinator(instanceRepository, activeTaskRepository, historyTaskRepository,
                taskGroupRepository, definitionLoader, requestValidator, operationExecutor, nodeAdvancer,
                stateValidator, historyTaskWriter, transactionExecutor, callbackService, organizationProvider,
                auditLogWriter);
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskClaimCoordinator taskClaimCoordinator(
            ProcessInstanceRepository instanceRepository,
            ActiveTaskRepository activeTaskRepository,
            RuntimeRequestValidator requestValidator,
            RuntimeOperationExecutor operationExecutor,
            RuntimeTransactionExecutor transactionExecutor,
            AuditLogWriter auditLogWriter,
            CallbackService callbackService,
            ObjectProvider<DelegateProvider> delegateProvider) {
        return new TaskClaimCoordinator(instanceRepository, activeTaskRepository, requestValidator,
                operationExecutor, transactionExecutor, auditLogWriter, callbackService,
                delegateProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public CountersignTaskCoordinator countersignTaskCoordinator(TaskGroupRepository taskGroupRepository,
                                                                 RuntimeNodeAdvancer nodeAdvancer) {
        return new CountersignTaskCoordinator(taskGroupRepository, nodeAdvancer);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessRuntimeService processRuntimeService(
            ProcessInstanceRepository instanceRepository,
            ActiveTaskRepository activeTaskRepository,
            HistoryTaskRepository historyTaskRepository,
            RuntimeDefinitionLoader definitionLoader,
            RuntimeRequestValidator requestValidator,
            RuntimeOperationExecutor operationExecutor,
            RuntimeNodeAdvancer nodeAdvancer,
            ObjectProvider<AttachmentService> attachmentService,
            CallbackService callbackService,
            RuntimeStateValidator runtimeStateValidator,
            HistoryTaskWriter historyTaskWriter,
            RuntimeTransactionExecutor transactionExecutor,
            InstanceTaskCancellationService cancellationService,
            ProcessInstanceDeletionRepository deletionRepository,
            TaskGroupRepository taskGroupRepository,
            ProcessDefinitionRepository definitionRepository,
            ObjectProvider<FileStorageProvider> fileStorageProvider,
            EnhancedTaskActionCoordinator enhancedTaskActionCoordinator,
            TaskClaimCoordinator taskClaimCoordinator,
            CountersignTaskCoordinator countersignTaskCoordinator) {
        DefaultProcessRuntimeService runtimeService = new DefaultProcessRuntimeService(
                instanceRepository, activeTaskRepository, historyTaskRepository,
                definitionLoader, requestValidator, operationExecutor, nodeAdvancer, attachmentService.getIfAvailable(),
                callbackService, runtimeStateValidator, historyTaskWriter, transactionExecutor,
                cancellationService, deletionRepository, taskGroupRepository, definitionRepository,
                fileStorageProvider.getIfAvailable());
        runtimeService.setEnhancedTaskActionCoordinator(enhancedTaskActionCoordinator);
        runtimeService.setTaskClaimCoordinator(taskClaimCoordinator);
        runtimeService.setCountersignTaskCoordinator(countersignTaskCoordinator);
        return runtimeService;
    }

    @Bean
    @ConditionalOnMissingBean
    public ReadRecordManager readRecordManager(ProcessReadRecordRepository readRecordRepository,
                                               ObjectProvider<CurrentUserProvider> currentUserProvider) {
        CurrentUserProvider currentUser = currentUserProvider.getIfAvailable();
        if (currentUser == null) {
            currentUser = new RequiredCurrentUserProvider();
        }
        return new ReadRecordManager(readRecordRepository, currentUser);
    }

    @Bean
    @ConditionalOnMissingBean
    public MonitorModelMapper monitorModelMapper() {
        return new MonitorModelMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskQueryService taskQueryService(ProcessHistoryTaskRepository historyTaskRepository,
                                             ActiveTaskRepository activeTaskRepository,
                                             ProcessInstanceRepository instanceRepository,
                                             ProcessTraceAssembler traceAssembler,
                                             RuntimeQueryAssembler queryAssembler,
                                             ReadRecordManager readRecordManager,
                                             ObjectProvider<CurrentUserProvider> currentUserProvider) {
        CurrentUserProvider currentUser = currentUserProvider.getIfAvailable();
        if (currentUser == null) {
            currentUser = new RequiredCurrentUserProvider();
        }
        return new DefaultTaskQueryService(historyTaskRepository, activeTaskRepository, instanceRepository,
                traceAssembler, queryAssembler, currentUser, readRecordManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public CallbackLogMapper callbackLogMapper() {
        return new CallbackLogMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public CallbackOutboxService callbackOutboxService(ProcessCallbackLogRepository callbackLogRepository,
                                                        CallbackLogMapper callbackLogMapper) {
        return new CallbackOutboxService(callbackLogRepository, callbackLogMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public CallbackService callbackService(ProcessCallbackLogRepository callbackLogRepository,
                                           CallbackLogMapper callbackLogMapper,
                                           CallbackOutboxService callbackOutboxService) {
        return new DefaultCallbackService(callbackLogRepository, callbackLogMapper, callbackOutboxService);
    }

    @Bean
    @ConditionalOnMissingBean
    public TimeoutPolicyReader timeoutPolicyReader() {
        return new TimeoutPolicyReader();
    }

    @Bean
    @ConditionalOnMissingBean
    public ReminderPolicyReader reminderPolicyReader() {
        return new ReminderPolicyReader();
    }

    @Bean
    @ConditionalOnMissingBean
    public ReminderDeduplicationGuard reminderDeduplicationGuard(ReminderRecordRepository reminderRepository) {
        return new ReminderDeduplicationGuard(reminderRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionExceptionAlertWriter actionExceptionAlertWriter(AlertRecordRepository alertRepository) {
        return new ActionExceptionAlertWriter(alertRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public AdminPermissionGuard adminPermissionGuard() {
        return new AdminPermissionGuard();
    }

    @Bean
    @ConditionalOnBean({AdminProcessService.class, ProcessRuntimeService.class})
    @ConditionalOnMissingBean
    public TimeoutActionExecutor timeoutActionExecutor(AdminProcessService adminProcessService,
                                                       ProcessRuntimeService processRuntimeService) {
        return new TimeoutActionExecutor(adminProcessService, processRuntimeService);
    }

    @Bean
    @ConditionalOnMissingBean
    public CallbackFailureAlertService callbackFailureAlertService(AlertRecordRepository alertRepository) {
        return new CallbackFailureAlertService(alertRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public CallbackDispatchService callbackDispatchService(ProcessCallbackLogRepository callbackLogRepository,
                                                           ObjectProvider<WorkflowCallbackHandler> callbackHandler,
                                                           CallbackFailureAlertService failureAlertService) {
        WorkflowCallbackHandler handler = callbackHandler.getIfAvailable();
        if (handler == null) {
            handler = new RequiredWorkflowCallbackHandler();
        }
        return new CallbackDispatchService(callbackLogRepository, handler, failureAlertService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "flow-mind.platform.callback", name = "async-enabled",
            havingValue = "true", matchIfMissing = true)
    public CallbackDispatchScheduler callbackDispatchScheduler(CallbackDispatchService dispatchService,
                                                               PlatformProperties properties) {
        PlatformProperties.Callback callback = properties.getCallback();
        return new CallbackDispatchScheduler(dispatchService, callback.getInitialDelayMs(),
                callback.getFixedDelayMs(), callback.getLimit());
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessMonitorService processMonitorService(ActiveTaskRepository activeTaskRepository,
                                                       ProcessInstanceRepository instanceRepository,
                                                       ReminderRecordRepository reminderRepository,
                                                       AlertRecordRepository alertRepository,
                                                       RuntimeRequestValidator requestValidator,
                                                       RuntimeOperationExecutor operationExecutor,
                                                       RuntimeTransactionExecutor transactionExecutor,
                                                       AuditLogWriter auditLogWriter,
                                                       ObjectProvider<MessagePublisher> messagePublisher,
                                                       ProcessNodeRepository processNodeRepository,
                                                       TimeoutPolicyReader timeoutPolicyReader,
                                                       ReminderPolicyReader reminderPolicyReader,
                                                       ReminderDeduplicationGuard reminderDeduplicationGuard,
                                                       ObjectProvider<TimeoutActionExecutor> timeoutActionExecutor,
                                                       ActionExceptionAlertWriter actionExceptionAlertWriter,
                                                       AdminPermissionGuard adminPermissionGuard) {
        MessagePublisher publisher = messagePublisher.getIfAvailable();
        if (publisher == null) {
            publisher = new RequiredMessagePublisher();
        }
        return new DefaultProcessMonitorService(activeTaskRepository, instanceRepository, reminderRepository,
                alertRepository, requestValidator, operationExecutor, transactionExecutor, auditLogWriter, publisher,
                processNodeRepository, timeoutPolicyReader, reminderPolicyReader, reminderDeduplicationGuard,
                timeoutActionExecutor.getIfAvailable(), actionExceptionAlertWriter, adminPermissionGuard);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "flow-mind.platform.timeout-scan", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public TimeoutScanScheduler timeoutScanScheduler(ProcessMonitorService monitorService,
                                                     PlatformProperties properties) {
        PlatformProperties.TimeoutScan timeoutScan = properties.getTimeoutScan();
        return new TimeoutScanScheduler(monitorService, timeoutScan.isEnabled(),
                timeoutScan.getInitialDelayMs(), timeoutScan.getFixedDelayMs(),
                timeoutScan.getLimit(), timeoutScan.getOperatorUserId());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "flow-mind.platform.mock", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public FileStorageProvider fileStorageProvider() {
        return new InMemoryFileStorageProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public AttachmentAccessProvider attachmentAccessProvider() {
        return new MockAttachmentAccessProvider(false);
    }

    @Bean
    @ConditionalOnMissingBean
    public AttachmentAccessGuard attachmentAccessGuard(AttachmentAccessProvider accessProvider) {
        return new AttachmentAccessGuard(accessProvider);
    }

    @Bean
    @ConditionalOnBean(FileStorageProvider.class)
    @ConditionalOnMissingBean
    public AttachmentService attachmentService(ProcessAttachmentRepository attachments,
                                               ProcessInstanceRepository instances,
                                               ActiveTaskRepository tasks,
                                               ProcessDefinitionAttachmentConfigRepository configs,
                                               ProcessAttachmentTemplateRepository templates,
                                               FileStorageProvider storage,
                                               AttachmentAccessGuard guard,
                                               CurrentUserProvider currentUser,
                                               RuntimeOperationExecutor operationExecutor,
                                               ProcessNodeRepository nodeRepository) {
        return new DefaultAttachmentService(attachments, instances, tasks, configs, templates, storage, guard, currentUser,
                operationExecutor, nodeRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "flow-mind.platform.mock", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public MessagePublisher messagePublisher() {
        return new RecordingMessagePublisher();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "flow-mind.platform.mock", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public WorkflowCallbackHandler workflowCallbackHandler() {
        return new RecordingWorkflowCallbackHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "flow-mind.platform.mock", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public OrganizationProvider organizationProvider() {
        return new InMemoryOrganizationProvider();
    }

    @Bean
    @ConditionalOnBean(OrganizationProvider.class)
    @ConditionalOnMissingBean
    public ApproverResolver approverResolver(OrganizationProvider organizationProvider) {
        return new DefaultApproverResolver(organizationProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "flow-mind.platform.mock", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public CurrentUserProvider currentUserProvider() {
        return new MockCurrentUserProvider();
    }

    public static final class PlatformSchemaInitializer implements InitializingBean {
        private static final String INIT_SCRIPT = "schema/sqlite/001_init_flow_platform.sql";
        private static final String M2_OPERATION_MIGRATION = "schema/sqlite/002_m2_runtime_operation_actions.sql";
        private static final String ATTACHMENT_OPERATION_MIGRATION =
                "schema/sqlite/003_attachment_operation_actions.sql";
        private static final String ATTACHMENT_REPLACE_OPERATION_MIGRATION =
                "schema/sqlite/004_attachment_replace_operation_action.sql";
        private static final String DELEGATE_FROM_USER_NAME_MIGRATION =
                "schema/sqlite/005_delegate_from_user_name.sql";
        private static final String DUE_SOON_REMINDER_TYPE_MIGRATION =
                "schema/sqlite/006_due_soon_reminder_type.sql";
        private static final String NOTICE_NODE_CONFIG_MIGRATION =
                "schema/sqlite/007_notice_node_config.sql";

        private final DataSource dataSource;

        public PlatformSchemaInitializer(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public void afterPropertiesSet() throws Exception {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            try {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource(INIT_SCRIPT));
                if (!operationRecordSupportsM2Actions(connection)) {
                    ScriptUtils.executeSqlScript(connection, new ClassPathResource(M2_OPERATION_MIGRATION));
                }
                if (!schemaSupportsAttachmentActions(connection)) {
                    ScriptUtils.executeSqlScript(connection, new ClassPathResource(ATTACHMENT_OPERATION_MIGRATION));
                }
                if (!schemaSupportsAttachmentReplacement(connection)) {
                    ScriptUtils.executeSqlScript(connection,
                            new ClassPathResource(ATTACHMENT_REPLACE_OPERATION_MIGRATION));
                }
                if (!tableHasColumn(connection, "process_active_task", "delegate_from_user_name")) {
                    ScriptUtils.executeSqlScript(connection,
                            new ClassPathResource(DELEGATE_FROM_USER_NAME_MIGRATION));
                }
                if (!tableContainsAction(connection, "process_reminder_record", "DUE_SOON")) {
                    ScriptUtils.executeSqlScript(connection,
                            new ClassPathResource(DUE_SOON_REMINDER_TYPE_MIGRATION));
                }
                if (!tableHasColumn(connection, "process_node", "notice_config")) {
                    ScriptUtils.executeSqlScript(connection,
                            new ClassPathResource(NOTICE_NODE_CONFIG_MIGRATION));
                }
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource);
            }
        }

        private boolean operationRecordSupportsM2Actions(Connection connection) throws Exception {
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT sql FROM sqlite_master WHERE type = 'table' "
                                 + "AND name = 'process_operation_record'")) {
                if (!resultSet.next() || resultSet.getString("sql") == null) {
                    return false;
                }
                String definition = resultSet.getString("sql");
                return definition.contains("START_AND_SUBMIT") && definition.contains("UPDATE_VARIABLES");
            }
        }

        private boolean schemaSupportsAttachmentActions(Connection connection) throws Exception {
            return tableSupportsAttachmentActions(connection, "process_operation_record")
                    && tableSupportsAttachmentActions(connection, "process_audit_log");
        }

        private boolean schemaSupportsAttachmentReplacement(Connection connection) throws Exception {
            return tableContainsAction(connection, "process_operation_record", "ATTACHMENT_REPLACE")
                    && tableContainsAction(connection, "process_audit_log", "ATTACHMENT_REPLACE");
        }

        private boolean tableSupportsAttachmentActions(Connection connection, String tableName) throws Exception {
            return tableContainsAction(connection, tableName, "ATTACHMENT_UPLOAD")
                    && tableContainsAction(connection, tableName, "ATTACHMENT_DELETE");
        }

        private boolean tableContainsAction(Connection connection, String tableName, String action) throws Exception {
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT sql FROM sqlite_master WHERE type = 'table' "
                                 + "AND name = '" + tableName + "'")) {
                if (!resultSet.next() || resultSet.getString("sql") == null) {
                    return false;
                }
                String definition = resultSet.getString("sql");
                return definition.contains(action);
            }
        }

        private boolean tableHasColumn(Connection connection, String tableName, String columnName) throws Exception {
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

    private static final class RequiredCurrentUserProvider implements CurrentUserProvider {
        @Override
        public UserContext getCurrentUser() {
            throw new IllegalStateException("CurrentUserProvider bean is required for TaskQueryService");
        }
    }

    private static final class RequiredMessagePublisher implements MessagePublisher {
        @Override
        public void publish(com.flowmind.platform.api.dto.ProcessMessage message) {
            throw new IllegalStateException("MessagePublisher bean is required for ProcessMonitorService");
        }
    }

    private static final class RequiredWorkflowCallbackHandler implements WorkflowCallbackHandler {
        @Override
        public void handle(com.flowmind.platform.api.dto.WorkflowEvent event) {
            throw new IllegalStateException("WorkflowCallbackHandler bean is required for callback dispatch");
        }
    }

}

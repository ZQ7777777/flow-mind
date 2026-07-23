package com.flowmind.platform.starter;

import com.flowmind.platform.api.dto.AlertDTO;
import com.flowmind.platform.api.dto.AttachmentDTO;
import com.flowmind.platform.api.dto.AttachmentDownloadDTO;
import com.flowmind.platform.api.dto.AttachmentTemplateCheckResult;
import com.flowmind.platform.api.dto.CallbackLogDTO;
import com.flowmind.platform.api.dto.DelegateRelationDTO;
import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessCommentDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.ReadRecordDTO;
import com.flowmind.platform.api.dto.ReminderDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.dto.WorkflowEvent;
import com.flowmind.platform.api.dto.AlertQuery;
import com.flowmind.platform.api.dto.AttachmentQuery;
import com.flowmind.platform.api.dto.CallbackLogQuery;
import com.flowmind.platform.api.dto.CompletedTaskQuery;
import com.flowmind.platform.api.dto.ReadRecordQuery;
import com.flowmind.platform.api.dto.ReminderQuery;
import com.flowmind.platform.api.dto.StartedInstanceQuery;
import com.flowmind.platform.api.dto.TodoTaskQuery;
import com.flowmind.platform.api.request.CheckAttachmentRequest;
import com.flowmind.platform.api.request.DeleteAttachmentRequest;
import com.flowmind.platform.api.request.DownloadAttachmentRequest;
import com.flowmind.platform.api.request.HandleAlertRequest;
import com.flowmind.platform.api.request.RemindTaskRequest;
import com.flowmind.platform.api.request.SaveInstanceAttachmentRequest;
import com.flowmind.platform.api.request.SaveTaskAttachmentRequest;
import com.flowmind.platform.api.request.StoreFileRequest;
import com.flowmind.platform.api.request.TimeoutScanRequest;
import com.flowmind.platform.api.service.AttachmentService;
import com.flowmind.platform.api.service.CallbackService;
import com.flowmind.platform.api.service.ProcessMonitorService;
import com.flowmind.platform.api.service.TaskQueryService;
import com.flowmind.platform.api.spi.AttachmentAccessProvider;
import com.flowmind.platform.api.spi.CurrentUserProvider;
import com.flowmind.platform.api.spi.DelegateProvider;
import com.flowmind.platform.api.spi.FileStorageProvider;
import com.flowmind.platform.api.spi.MessagePublisher;
import com.flowmind.platform.api.spi.WorkflowCallbackHandler;
import com.flowmind.platform.api.dto.FileContent;
import com.flowmind.platform.api.dto.ProcessMessage;
import com.flowmind.platform.api.dto.StoredFile;
import com.flowmind.platform.core.query.DefaultTaskQueryService;
import com.flowmind.platform.core.query.ProcessTraceAssembler;
import com.flowmind.platform.core.query.RuntimeQueryAssembler;
import com.flowmind.platform.core.security.AttachmentAccessGuard;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessHistoryTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import com.flowmind.platform.starter.properties.PlatformProperties;
import org.sqlite.SQLiteDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableConfigurationProperties(PlatformProperties.class)
@ConditionalOnProperty(prefix = "flow-mind.platform", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PlatformAutoConfiguration {

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
    public TaskQueryService taskQueryService(ProcessHistoryTaskRepository historyTaskRepository,
                                             ActiveTaskRepository activeTaskRepository,
                                             ProcessInstanceRepository instanceRepository,
                                             ProcessTraceAssembler traceAssembler,
                                             RuntimeQueryAssembler queryAssembler,
                                             ObjectProvider<CurrentUserProvider> currentUserProvider,
                                             ObjectProvider<DelegateProvider> delegateProvider) {
        CurrentUserProvider currentUser = currentUserProvider.getIfAvailable();
        if (currentUser == null) {
            currentUser = new RequiredCurrentUserProvider();
        }
        DelegateProvider delegates = delegateProvider.getIfAvailable();
        if (delegates == null) {
            delegates = (principalUserId, at) -> Collections.<DelegateRelationDTO>emptyList();
        }
        return new DefaultTaskQueryService(historyTaskRepository, activeTaskRepository, instanceRepository,
                traceAssembler, queryAssembler, currentUser, delegates);
    }

    @Bean
    @ConditionalOnMissingBean
    public AttachmentService attachmentService() {
        return new UnsupportedAttachmentService();
    }

    @Bean
    @ConditionalOnMissingBean
    public CallbackService callbackService() {
        return new UnsupportedCallbackService();
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessMonitorService processMonitorService() {
        return new UnsupportedProcessMonitorService();
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
        return request -> false;
    }

    @Bean
    @ConditionalOnMissingBean
    public AttachmentAccessGuard attachmentAccessGuard(AttachmentAccessProvider accessProvider) {
        return new AttachmentAccessGuard(accessProvider);
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
        return event -> {
        };
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "flow-mind.platform.mock", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public DelegateProvider delegateProvider() {
        return (principalUserId, at) -> Collections.<DelegateRelationDTO>emptyList();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "flow-mind.platform.mock", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public CurrentUserProvider currentUserProvider() {
        return () -> new UserContext("mock-user", "Mock User", "mock-dept", "Mock Department");
    }

    public static final class PlatformSchemaInitializer implements InitializingBean {
        private static final String INIT_SCRIPT = "schema/sqlite/001_init_flow_platform.sql";
        private static final String M2_OPERATION_MIGRATION = "schema/sqlite/002_m2_runtime_operation_actions.sql";

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
    }

    private static final class RequiredCurrentUserProvider implements CurrentUserProvider {
        @Override
        public UserContext getCurrentUser() {
            throw new IllegalStateException("CurrentUserProvider bean is required for TaskQueryService");
        }
    }

    private abstract static class UnsupportedPlatformService {
        protected final UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("Platform service implementation is not wired in this starter skeleton");
        }
    }

    private static final class UnsupportedAttachmentService extends UnsupportedPlatformService
            implements AttachmentService {

        @Override
        public AttachmentDTO saveInstanceAttachment(SaveInstanceAttachmentRequest request) {
            throw unsupported();
        }

        @Override
        public AttachmentDTO saveTaskAttachment(SaveTaskAttachmentRequest request) {
            throw unsupported();
        }

        @Override
        public AttachmentDownloadDTO downloadAttachment(DownloadAttachmentRequest request) {
            throw unsupported();
        }

        @Override
        public List<AttachmentDTO> queryAttachments(AttachmentQuery query) {
            throw unsupported();
        }

        @Override
        public void deleteAttachment(DeleteAttachmentRequest request) {
            throw unsupported();
        }

        @Override
        public AttachmentTemplateCheckResult checkRequiredAttachments(CheckAttachmentRequest request) {
            throw unsupported();
        }
    }

    private static final class UnsupportedCallbackService extends UnsupportedPlatformService
            implements CallbackService {

        @Override
        public void publishCallback(WorkflowEvent event) {
            throw unsupported();
        }

        @Override
        public PageResult<CallbackLogDTO> queryCallbackLogs(CallbackLogQuery query) {
            throw unsupported();
        }
    }

    private static final class UnsupportedProcessMonitorService extends UnsupportedPlatformService
            implements ProcessMonitorService {

        @Override
        public ReminderDTO remindTask(RemindTaskRequest request) {
            throw unsupported();
        }

        @Override
        public PageResult<ReminderDTO> queryReminders(ReminderQuery query) {
            throw unsupported();
        }

        @Override
        public List<TaskDTO> scanTimeoutTasks(TimeoutScanRequest request) {
            throw unsupported();
        }

        @Override
        public PageResult<AlertDTO> queryAlerts(AlertQuery query) {
            throw unsupported();
        }

        @Override
        public AlertDTO handleAlert(HandleAlertRequest request) {
            throw unsupported();
        }
    }

    public static final class InMemoryFileStorageProvider implements FileStorageProvider {
        private final Map<String, FileContent> files = new ConcurrentHashMap<String, FileContent>();

        @Override
        public StoredFile store(StoreFileRequest request) {
            String storageKey = "mock://" + UUID.randomUUID().toString();
            FileContent content = new FileContent(storageKey, request.getFileName(), request.getContentType(),
                    request.getSizeBytes(), request.getContent());
            files.put(storageKey, content);
            return new StoredFile(storageKey, request.getFileName(), request.getContentType(), request.getSizeBytes());
        }

        @Override
        public FileContent load(String storageKey) {
            FileContent content = files.get(storageKey);
            if (content == null) {
                throw new IllegalArgumentException("File not found: " + storageKey);
            }
            return content;
        }

        @Override
        public void delete(String storageKey) {
            files.remove(storageKey);
        }
    }

    public static final class RecordingMessagePublisher implements MessagePublisher {
        private final List<ProcessMessage> messages =
                Collections.synchronizedList(new java.util.ArrayList<ProcessMessage>());

        @Override
        public void publish(ProcessMessage message) {
            if (message.getCreatedAt() == null) {
                message.setCreatedAt(LocalDateTime.now());
            }
            messages.add(message);
        }

        public List<ProcessMessage> getMessages() {
            return messages;
        }
    }
}

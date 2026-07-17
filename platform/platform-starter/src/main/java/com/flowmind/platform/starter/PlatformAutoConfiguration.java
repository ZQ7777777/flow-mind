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
import com.flowmind.platform.core.security.AttachmentAccessGuard;
import com.flowmind.platform.starter.properties.PlatformProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public TaskQueryService taskQueryService() {
        return new UnsupportedTaskQueryService();
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

    private abstract static class UnsupportedPlatformService {
        protected final UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("Platform service implementation is not wired in this starter skeleton");
        }
    }

    private static final class UnsupportedTaskQueryService extends UnsupportedPlatformService
            implements TaskQueryService {

        @Override
        public PageResult<TaskDTO> queryTodoTasks(TodoTaskQuery query) {
            throw unsupported();
        }

        @Override
        public PageResult<HistoryTaskDTO> queryCompletedTasks(CompletedTaskQuery query) {
            throw unsupported();
        }

        @Override
        public PageResult<ProcessInstanceDTO> queryStartedInstances(StartedInstanceQuery query) {
            throw unsupported();
        }

        @Override
        public List<TaskDTO> queryActiveTasks(String instanceId) {
            throw unsupported();
        }

        @Override
        public List<HistoryTaskDTO> queryHistoryTasks(String instanceId) {
            throw unsupported();
        }

        @Override
        public List<ProcessCommentDTO> queryComments(String instanceId) {
            throw unsupported();
        }

        @Override
        public PageResult<ReadRecordDTO> queryReadRecords(ReadRecordQuery query) {
            throw unsupported();
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

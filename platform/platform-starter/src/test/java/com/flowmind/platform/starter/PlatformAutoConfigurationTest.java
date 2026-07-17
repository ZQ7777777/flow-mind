package com.flowmind.platform.starter;

import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessCommentDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.ReadRecordDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.CompletedTaskQuery;
import com.flowmind.platform.api.dto.ReadRecordQuery;
import com.flowmind.platform.api.dto.StartedInstanceQuery;
import com.flowmind.platform.api.dto.TodoTaskQuery;
import com.flowmind.platform.api.request.StoreFileRequest;
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
import com.flowmind.platform.api.dto.StoredFile;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlatformAutoConfiguration.class));

    @Test
    void autoConfigurationProvidesCLineServicesAndMockSpis() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TaskQueryService.class);
            assertThat(context).hasSingleBean(AttachmentService.class);
            assertThat(context).hasSingleBean(CallbackService.class);
            assertThat(context).hasSingleBean(ProcessMonitorService.class);
            assertThat(context).hasSingleBean(FileStorageProvider.class);
            assertThat(context).hasSingleBean(AttachmentAccessProvider.class);
            assertThat(context).hasSingleBean(MessagePublisher.class);
            assertThat(context).hasSingleBean(WorkflowCallbackHandler.class);
            assertThat(context).hasSingleBean(DelegateProvider.class);
            assertThat(context).hasSingleBean(CurrentUserProvider.class);
        });
    }

    @Test
    void customSpiBeanOverridesDefaultMock() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PlatformAutoConfiguration.class))
                .withUserConfiguration(CustomSpiConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FileStorageProvider.class);
                    assertThat(context.getBean(FileStorageProvider.class))
                            .isInstanceOf(CustomFileStorageProvider.class);
                });
    }

    @Test
    void customServiceBeanOverridesStarterSkeleton() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PlatformAutoConfiguration.class))
                .withUserConfiguration(CustomServiceConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(TaskQueryService.class);
                    assertThat(context.getBean(TaskQueryService.class))
                            .isInstanceOf(CustomTaskQueryService.class);
                });
    }

    @Test
    void mockSpisCanBeDisabledByProperty() {
        contextRunner
                .withPropertyValues("flow-mind.platform.mock.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FileStorageProvider.class);
                    assertThat(context).doesNotHaveBean(AttachmentAccessProvider.class);
                    assertThat(context).doesNotHaveBean(MessagePublisher.class);
                    assertThat(context).doesNotHaveBean(WorkflowCallbackHandler.class);
                    assertThat(context).doesNotHaveBean(DelegateProvider.class);
                    assertThat(context).doesNotHaveBean(CurrentUserProvider.class);
                    assertThat(context).hasSingleBean(TaskQueryService.class);
                });
    }

    @Configuration
    static class CustomSpiConfiguration {
        @Bean
        FileStorageProvider customFileStorageProvider() {
            return new CustomFileStorageProvider();
        }
    }

    @Configuration
    static class CustomServiceConfiguration {
        @Bean
        TaskQueryService customTaskQueryService() {
            return new CustomTaskQueryService();
        }
    }

    static class CustomFileStorageProvider implements FileStorageProvider {
        @Override
        public StoredFile store(StoreFileRequest request) {
            return new StoredFile("custom-key", request.getFileName(), request.getContentType(),
                    request.getSizeBytes());
        }

        @Override
        public FileContent load(String storageKey) {
            return new FileContent(storageKey, "custom.txt", "text/plain", Long.valueOf(0L), new byte[0]);
        }

        @Override
        public void delete(String storageKey) {
        }
    }

    static class CustomTaskQueryService implements TaskQueryService {
        @Override
        public PageResult<TaskDTO> queryTodoTasks(TodoTaskQuery query) {
            return null;
        }

        @Override
        public PageResult<HistoryTaskDTO> queryCompletedTasks(CompletedTaskQuery query) {
            return null;
        }

        @Override
        public PageResult<ProcessInstanceDTO> queryStartedInstances(StartedInstanceQuery query) {
            return null;
        }

        @Override
        public List<TaskDTO> queryActiveTasks(String instanceId) {
            return null;
        }

        @Override
        public List<HistoryTaskDTO> queryHistoryTasks(String instanceId) {
            return null;
        }

        @Override
        public List<ProcessCommentDTO> queryComments(String instanceId) {
            return null;
        }

        @Override
        public PageResult<ReadRecordDTO> queryReadRecords(ReadRecordQuery query) {
            return null;
        }
    }
}

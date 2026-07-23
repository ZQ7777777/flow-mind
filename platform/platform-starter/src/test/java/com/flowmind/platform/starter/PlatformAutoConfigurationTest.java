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
import com.flowmind.platform.api.request.AttachmentAccessRequest;
import com.flowmind.platform.core.query.DefaultTaskQueryService;
import com.flowmind.platform.core.security.AttachmentAccessGuard;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessHistoryTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlatformAutoConfiguration.class));

    @TempDir
    Path tempDir;

    @Test
    void autoConfigurationProvidesCLineServicesAndMockSpis() {
        contextRunnerWithDatabase("default.db").run(context -> {
            assertThat(context).hasSingleBean(DataSource.class);
            assertThat(context).hasSingleBean(JdbcTemplate.class);
            assertThat(context).hasSingleBean(ProcessHistoryTaskRepository.class);
            assertThat(context).hasSingleBean(ActiveTaskRepository.class);
            assertThat(context).hasSingleBean(ProcessInstanceRepository.class);
            assertThat(context).hasSingleBean(TaskQueryService.class);
            assertThat(context.getBean(TaskQueryService.class)).isInstanceOf(DefaultTaskQueryService.class);
            assertThat(context).hasSingleBean(AttachmentService.class);
            assertThat(context).hasSingleBean(CallbackService.class);
            assertThat(context).hasSingleBean(ProcessMonitorService.class);
            assertThat(context).hasSingleBean(FileStorageProvider.class);
            assertThat(context).hasSingleBean(AttachmentAccessProvider.class);
            assertThat(context).hasSingleBean(AttachmentAccessGuard.class);
            assertThat(context.getBean(AttachmentAccessProvider.class)
                    .isAllowed(new AttachmentAccessRequest())).isFalse();
            assertThat(context).hasSingleBean(MessagePublisher.class);
            assertThat(context).hasSingleBean(WorkflowCallbackHandler.class);
            assertThat(context).hasSingleBean(DelegateProvider.class);
            assertThat(context).hasSingleBean(CurrentUserProvider.class);
        });
    }

    @Test
    void autoConfiguredTaskQueryServiceReadsFromInitializedSqlite() {
        contextRunnerWithDatabase("query.db").run(context -> {
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            insertQueryFixtures(jdbcTemplate);

            TaskQueryService service = context.getBean(TaskQueryService.class);

            PageResult<TaskDTO> todoTasks = service.queryTodoTasks(new TodoTaskQuery());
            assertThat(todoTasks.getTotal()).isEqualTo(Long.valueOf(1L));
            assertThat(todoTasks.getRecords()).hasSize(1);
            assertThat(todoTasks.getRecords().get(0).getTaskId()).isEqualTo("task-001");
            assertThat(todoTasks.getRecords().get(0).getTaskVersion()).isEqualTo(Long.valueOf(7L));

            PageResult<HistoryTaskDTO> completedTasks = service.queryCompletedTasks(new CompletedTaskQuery());
            assertThat(completedTasks.getTotal()).isEqualTo(Long.valueOf(1L));
            assertThat(completedTasks.getRecords()).extracting(HistoryTaskDTO::getHistoryTaskId)
                    .containsExactly("history-001");

            PageResult<ProcessInstanceDTO> startedInstances =
                    service.queryStartedInstances(new StartedInstanceQuery());
            assertThat(startedInstances.getTotal()).isEqualTo(Long.valueOf(1L));
            assertThat(startedInstances.getRecords()).extracting(ProcessInstanceDTO::getInstanceId)
                    .containsExactly("instance-001");

            assertThat(service.queryActiveTasks("instance-001")).extracting(TaskDTO::getTaskId)
                    .containsExactly("task-001");
        });
    }

    @Test
    void customSpiBeanOverridesDefaultMock() {
        contextRunnerWithDatabase("custom-spi.db")
                .withUserConfiguration(CustomSpiConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FileStorageProvider.class);
                    assertThat(context.getBean(FileStorageProvider.class))
                            .isInstanceOf(CustomFileStorageProvider.class);
                });
    }

    @Test
    void customAttachmentAccessExceptionIsConvertedToDenial() {
        contextRunnerWithDatabase("access.db")
                .withUserConfiguration(ThrowingAccessProviderConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(AttachmentAccessProvider.class);
                    assertThat(context).hasSingleBean(AttachmentAccessGuard.class);
                    assertThat(context.getBean(AttachmentAccessGuard.class)
                            .isAllowed(new AttachmentAccessRequest())).isFalse();
                });
    }

    @Test
    void customServiceBeanOverridesStarterSkeleton() {
        contextRunnerWithDatabase("custom-service.db")
                .withUserConfiguration(CustomServiceConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(TaskQueryService.class);
                    assertThat(context.getBean(TaskQueryService.class))
                            .isInstanceOf(CustomTaskQueryService.class);
                });
    }

    @Test
    void customDataSourceOverridesDefaultSqliteAndStillInitializesSchema() {
        contextRunner
                .withUserConfiguration(CustomDataSourceConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(DataSource.class);
                    assertThat(context.getBean(DataSource.class)).isInstanceOf(SingleConnectionDataSource.class);
                    Integer count = context.getBean(JdbcTemplate.class).queryForObject(
                            "SELECT COUNT(1) FROM sqlite_master WHERE type = 'table' "
                                    + "AND name = 'process_instance'",
                            Integer.class);
                    assertThat(count).isEqualTo(Integer.valueOf(1));
                });
    }

    @Test
    void customJdbcTemplateOverridesDefaultJdbcTemplate() {
        contextRunnerWithDatabase("custom-jdbc.db")
                .withUserConfiguration(CustomJdbcTemplateConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(JdbcTemplate.class);
                    assertThat(context.getBean(JdbcTemplate.class)).isInstanceOf(CustomJdbcTemplate.class);
                });
    }

    @Test
    void optionalMockSpisCanBeDisabledButDenyAllAccessRemains() {
        contextRunnerWithDatabase("mock-disabled.db")
                .withPropertyValues("flow-mind.platform.mock.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FileStorageProvider.class);
                    assertThat(context).hasSingleBean(AttachmentAccessProvider.class);
                    assertThat(context).hasSingleBean(AttachmentAccessGuard.class);
                    assertThat(context.getBean(AttachmentAccessGuard.class)
                            .isAllowed(new AttachmentAccessRequest())).isFalse();
                    assertThat(context).doesNotHaveBean(MessagePublisher.class);
                    assertThat(context).doesNotHaveBean(WorkflowCallbackHandler.class);
                    assertThat(context).doesNotHaveBean(DelegateProvider.class);
                    assertThat(context).doesNotHaveBean(CurrentUserProvider.class);
                    assertThat(context).hasSingleBean(TaskQueryService.class);
                    assertThat(context.getBean(TaskQueryService.class)).isInstanceOf(DefaultTaskQueryService.class);
                    assertThatThrownBy(() -> context.getBean(TaskQueryService.class)
                            .queryTodoTasks(new TodoTaskQuery()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("CurrentUserProvider bean is required");
                });
    }

    @Test
    void platformBeansAreNotCreatedWhenPlatformIsDisabled() {
        contextRunner
                .withPropertyValues("flow-mind.platform.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(TaskQueryService.class);
                    assertThat(context).doesNotHaveBean(JdbcTemplate.class);
                    assertThat(context).doesNotHaveBean(DataSource.class);
                });
    }

    private ApplicationContextRunner contextRunnerWithDatabase(String databaseName) {
        return contextRunner.withPropertyValues("flow-mind.platform.sqlite.path="
                + tempDir.resolve(databaseName).toAbsolutePath().toString().replace('\\', '/'));
    }

    private void insertQueryFixtures(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("INSERT INTO process_definition "
                        + "(id, process_code, process_name, system_code, version, definition_status, "
                        + "activation_status, gray_status, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "definition-001", "expense", "Expense", "oa", Integer.valueOf(1), "PUBLISHED",
                "ACTIVE", "OFF", "admin");
        jdbcTemplate.update("INSERT INTO process_node "
                        + "(id, definition_id, node_code, node_name, node_type, sort_order) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "node-001", "definition-001", "approve", "Approve", "USER_TASK", Integer.valueOf(1));
        jdbcTemplate.update("INSERT INTO process_instance "
                        + "(id, definition_id, process_code, process_name, version, instance_title, "
                        + "business_key, starter_user_id, starter_user_name, starter_dept_id, "
                        + "current_node_codes, variables_json, instance_status, started_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "instance-001", "definition-001", "expense", "Expense", Integer.valueOf(1),
                "Expense Instance", "biz-001", "mock-user", "Mock User", "mock-dept",
                "[\"approve\"]", "{}", "RUNNING", "2026-07-23 09:00:00");
        jdbcTemplate.update("INSERT INTO process_active_task "
                        + "(id, instance_id, definition_id, node_code, candidate_user_ids, assignee_user_id, "
                        + "assignee_user_name, task_status, lock_version, created_at, due_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "task-001", "instance-001", "definition-001", "approve", "[\"mock-user\"]",
                "mock-user", "Mock User", "ACTIVE", Long.valueOf(7L),
                "2026-07-23 09:10:00", "2026-07-24 09:10:00");
        jdbcTemplate.update("INSERT INTO process_history_task "
                        + "(id, instance_id, operation_id, active_task_id, node_code, assignee_user_id, "
                        + "assignee_user_name, handle_type, action_type, comment_text, variables_snapshot, "
                        + "started_at, completed_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "history-001", "instance-001", "operation-001", "task-history-001", "approve",
                "mock-user", "Mock User", "NORMAL", "APPROVE", "approved", "{}",
                "2026-07-23 08:50:00", "2026-07-23 09:20:00");
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

    @Configuration
    static class CustomDataSourceConfiguration {
        @Bean
        DataSource customDataSource() throws SQLException {
            return new SingleConnectionDataSource(DriverManager.getConnection("jdbc:sqlite::memory:"), true);
        }
    }

    @Configuration
    static class CustomJdbcTemplateConfiguration {
        @Bean
        JdbcTemplate customJdbcTemplate(DataSource dataSource) {
            return new CustomJdbcTemplate(dataSource);
        }
    }

    @Configuration
    static class ThrowingAccessProviderConfiguration {
        @Bean
        AttachmentAccessProvider throwingAttachmentAccessProvider() {
            return request -> {
                throw new IllegalStateException("authorization unavailable");
            };
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

    static class CustomJdbcTemplate extends JdbcTemplate {
        CustomJdbcTemplate(DataSource dataSource) {
            super(dataSource);
        }
    }
}

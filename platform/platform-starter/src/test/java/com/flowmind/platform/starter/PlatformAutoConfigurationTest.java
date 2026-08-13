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
import com.flowmind.platform.api.request.TimeoutScanRequest;
import com.flowmind.platform.api.request.JumpNodeRequest;
import com.flowmind.platform.api.service.AdminProcessService;
import com.flowmind.platform.api.service.AttachmentService;
import com.flowmind.platform.api.service.CallbackService;
import com.flowmind.platform.api.service.ProcessMonitorService;
import com.flowmind.platform.api.service.ProcessDefinitionService;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import com.flowmind.platform.api.service.ReadRecordService;
import com.flowmind.platform.api.service.TaskQueryService;
import com.flowmind.platform.api.spi.AttachmentAccessProvider;
import com.flowmind.platform.api.spi.ApproverResolver;
import com.flowmind.platform.api.spi.CurrentUserProvider;
import com.flowmind.platform.api.spi.DelegateProvider;
import com.flowmind.platform.api.spi.FileStorageProvider;
import com.flowmind.platform.api.spi.MessagePublisher;
import com.flowmind.platform.api.spi.OrganizationProvider;
import com.flowmind.platform.api.spi.WorkflowCallbackHandler;
import com.flowmind.platform.api.dto.FileContent;
import com.flowmind.platform.api.dto.StoredFile;
import com.flowmind.platform.api.request.AttachmentAccessRequest;
import com.flowmind.platform.core.callback.CallbackDispatchService;
import com.flowmind.platform.core.callback.CallbackFailureAlertService;
import com.flowmind.platform.core.monitor.ActionExceptionAlertWriter;
import com.flowmind.platform.core.monitor.ReminderDeduplicationGuard;
import com.flowmind.platform.core.monitor.ReminderPolicyReader;
import com.flowmind.platform.core.monitor.TimeoutActionExecutor;
import com.flowmind.platform.core.monitor.TimeoutPolicyReader;
import com.flowmind.platform.core.monitor.TimeoutScanScheduler;
import com.flowmind.platform.core.query.DefaultTaskQueryService;
import com.flowmind.platform.core.runtime.DefaultApproverResolver;
import com.flowmind.platform.core.runtime.AdminPermissionGuard;
import com.flowmind.platform.core.runtime.CountersignTaskCoordinator;
import com.flowmind.platform.core.runtime.DefaultProcessRuntimeService;
import com.flowmind.platform.core.runtime.EnhancedTaskActionCoordinator;
import com.flowmind.platform.core.runtime.TaskClaimCoordinator;
import com.flowmind.platform.core.security.AttachmentAccessGuard;
import com.flowmind.platform.mock.InMemoryFileStorageProvider;
import com.flowmind.platform.mock.InMemoryOrganizationProvider;
import com.flowmind.platform.mock.MockAttachmentAccessProvider;
import com.flowmind.platform.mock.MockCurrentUserProvider;
import com.flowmind.platform.mock.RecordingMessagePublisher;
import com.flowmind.platform.mock.RecordingWorkflowCallbackHandler;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessHistoryTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import com.flowmind.platform.persistence.repository.ProcessCallbackLogRepository;
import com.flowmind.platform.persistence.repository.ProcessNodeRepository;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PlatformAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlatformAutoConfiguration.class));

    private final ApplicationContextRunner docsContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlatformKnife4jAutoConfiguration.class));

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
            assertThat(context).hasSingleBean(ReadRecordService.class);
            assertThat(context).hasSingleBean(ProcessDefinitionService.class);
            assertThat(context).hasSingleBean(ProcessRuntimeService.class);
            assertThat(context).hasSingleBean(EnhancedTaskActionCoordinator.class);
            assertThat(context).hasSingleBean(TaskClaimCoordinator.class);
            assertThat(context).hasSingleBean(CountersignTaskCoordinator.class);
            DefaultProcessRuntimeService runtimeService =
                    (DefaultProcessRuntimeService) context.getBean(ProcessRuntimeService.class);
            assertThat(ReflectionTestUtils.getField(runtimeService, "enhancedTaskActionCoordinator"))
                    .isSameAs(context.getBean(EnhancedTaskActionCoordinator.class));
            assertThat(ReflectionTestUtils.getField(runtimeService, "taskClaimCoordinator"))
                    .isSameAs(context.getBean(TaskClaimCoordinator.class));
            assertThat(ReflectionTestUtils.getField(runtimeService, "countersignTaskCoordinator"))
                    .isSameAs(context.getBean(CountersignTaskCoordinator.class));
            assertThat(context.getBean(TaskQueryService.class)).isInstanceOf(DefaultTaskQueryService.class);
            assertThat(context).hasSingleBean(AttachmentService.class);
            assertThat(context).hasSingleBean(CallbackService.class);
            assertThat(context).hasSingleBean(ProcessMonitorService.class);
            assertThat(context).hasSingleBean(ProcessNodeRepository.class);
            assertThat(context).hasSingleBean(ProcessCallbackLogRepository.class);
            assertThat(context).hasSingleBean(TimeoutPolicyReader.class);
            assertThat(context).hasSingleBean(TimeoutScanScheduler.class);
            assertThat(context).hasSingleBean(ReminderPolicyReader.class);
            assertThat(context).hasSingleBean(ReminderDeduplicationGuard.class);
            assertThat(context).hasSingleBean(ActionExceptionAlertWriter.class);
            assertThat(context).hasSingleBean(AdminPermissionGuard.class);
            assertThat(context).hasSingleBean(CallbackFailureAlertService.class);
            assertThat(context).hasSingleBean(CallbackDispatchService.class);
            assertThat(context).hasSingleBean(FileStorageProvider.class);
            assertThat(context.getBean(FileStorageProvider.class)).isInstanceOf(InMemoryFileStorageProvider.class);
            assertThat(context).hasSingleBean(AttachmentAccessProvider.class);
            assertThat(context).hasSingleBean(AttachmentAccessGuard.class);
            assertThat(context.getBean(AttachmentAccessProvider.class)).isInstanceOf(MockAttachmentAccessProvider.class);
            assertThat(context.getBean(AttachmentAccessProvider.class)
                    .isAllowed(new AttachmentAccessRequest())).isFalse();
            assertThat(context).hasSingleBean(MessagePublisher.class);
            assertThat(context.getBean(MessagePublisher.class)).isInstanceOf(RecordingMessagePublisher.class);
            assertThat(context).hasSingleBean(WorkflowCallbackHandler.class);
            assertThat(context.getBean(WorkflowCallbackHandler.class))
                    .isInstanceOf(RecordingWorkflowCallbackHandler.class);
            assertThat(context).hasSingleBean(CurrentUserProvider.class);
            assertThat(context.getBean(CurrentUserProvider.class)).isInstanceOf(MockCurrentUserProvider.class);
            assertThat(context).hasSingleBean(OrganizationProvider.class);
            assertThat(context.getBean(OrganizationProvider.class)).isInstanceOf(InMemoryOrganizationProvider.class);
            assertThat(context).hasSingleBean(ApproverResolver.class);
            assertThat(context.getBean(ApproverResolver.class)).isInstanceOf(DefaultApproverResolver.class);
        });
    }

    @Test
    void timeoutScanSchedulerCanBeDisabledByConfiguration() {
        contextRunnerWithDatabase("timeout-scheduler-disabled.db")
                .withPropertyValues("flow-mind.platform.timeout-scan.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(TimeoutScanScheduler.class));
    }

    @Test
    void starterMigratesLegacyReminderSchemaToSupportDueSoon() throws Exception {
        Path database = tempDir.resolve("legacy-reminder.db").toAbsolutePath();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE process_reminder_record ("
                    + "id TEXT PRIMARY KEY, instance_id TEXT NOT NULL, task_id TEXT, "
                    + "reminder_type TEXT NOT NULL CHECK (reminder_type IN ('MANUAL', 'AUTO', 'TIMEOUT')), "
                    + "target_user_ids TEXT NOT NULL, message TEXT NOT NULL, "
                    + "reminder_status TEXT NOT NULL DEFAULT 'PENDING' "
                    + "CHECK (reminder_status IN ('PENDING', 'SENT', 'FAILED')), "
                    + "error_message TEXT, created_by TEXT NOT NULL, "
                    + "created_at TEXT NOT NULL DEFAULT (datetime('now')), sent_at TEXT)");
        }

        contextRunner.withPropertyValues("flow-mind.platform.sqlite.path="
                        + database.toString().replace('\\', '/'),
                        "flow-mind.platform.timeout-scan.enabled=false")
                .run(context -> {
                    String definition = context.getBean(JdbcTemplate.class).queryForObject(
                            "SELECT sql FROM sqlite_master WHERE type = 'table' "
                                    + "AND name = 'process_reminder_record'",
                            String.class);
                    assertThat(definition).contains("DUE_SOON");
                });
    }

    @Test
    void autoConfiguredMonitorExecutesNodeTimeoutActionThroughHostRuntimeServices() {
        contextRunnerWithDatabase("m6-timeout-action.db")
                .withUserConfiguration(RuntimeServiceConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(TimeoutActionExecutor.class);
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                    insertTimeoutFixtures(jdbcTemplate);

                    TimeoutScanRequest request = new TimeoutScanRequest();
                    request.setDryRun(Boolean.FALSE);
                    request.setScanAt(LocalDateTime.of(2026, 7, 29, 12, 0));
                    request.setOperatorUserId("admin");
                    request.setLimit(Integer.valueOf(10));
                    context.getBean(ProcessMonitorService.class).scanTimeoutTasks(request);

                    ArgumentCaptor<JumpNodeRequest> requestCaptor = ArgumentCaptor.forClass(JumpNodeRequest.class);
                    verify(context.getBean(AdminProcessService.class)).jumpToNode(requestCaptor.capture());
                    assertThat(requestCaptor.getValue().getInstanceId()).isEqualTo("instance-timeout");
                    assertThat(requestCaptor.getValue().getTargetNodeCode()).isEqualTo("fallback");
                    assertThat(requestCaptor.getValue().getOperatorUserId()).isEqualTo("admin");
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
                    assertThat(context).hasSingleBean(MessagePublisher.class);
                    assertThat(context.getBean(MessagePublisher.class))
                            .isInstanceOf(CustomMessagePublisher.class);
                });
    }

    @Test
    void organizationProviderEnablesDefaultApproverResolver() {
        contextRunnerWithDatabase("organization-resolver.db")
                .withUserConfiguration(OrganizationProviderConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(OrganizationProvider.class);
                    assertThat(context).hasSingleBean(ApproverResolver.class);
                    ApproverResolver resolver = context.getBean(ApproverResolver.class);
                    assertThat(resolver).isInstanceOf(DefaultApproverResolver.class);
                    com.flowmind.platform.api.request.ApproverResolveRequest request =
                            new com.flowmind.platform.api.request.ApproverResolveRequest();
                    request.setApproverRuleType(com.flowmind.platform.api.enums.ApproverRuleTypeEnum.STARTER);
                    request.setStarterUserId("host-user");
                    assertThat(resolver.resolveApprovers(request)).extracting("userId")
                            .containsExactly("host-user");
                });
    }

    @Test
    void customApproverResolverOverridesDefaultResolver() {
        contextRunnerWithDatabase("custom-approver-resolver.db")
                .withUserConfiguration(OrganizationProviderConfiguration.class, CustomApproverResolverConfiguration.class)
                .run(context -> assertThat(context.getBean(ApproverResolver.class))
                        .isInstanceOf(CustomApproverResolver.class));
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
                    assertThat(context).doesNotHaveBean(AttachmentService.class);
                    assertThat(context).hasSingleBean(AttachmentAccessProvider.class);
                    assertThat(context).hasSingleBean(AttachmentAccessGuard.class);
                    assertThat(context.getBean(AttachmentAccessGuard.class)
                            .isAllowed(new AttachmentAccessRequest())).isFalse();
                    assertThat(context).doesNotHaveBean(MessagePublisher.class);
                    assertThat(context).doesNotHaveBean(WorkflowCallbackHandler.class);
                    assertThat(context).doesNotHaveBean(DelegateProvider.class);
                    assertThat(context).doesNotHaveBean(CurrentUserProvider.class);
                    assertThat(context).doesNotHaveBean(OrganizationProvider.class);
                    assertThat(context).doesNotHaveBean(ApproverResolver.class);
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

    @Test
    void knife4jDocsAreAutoConfiguredByDefault() {
        docsContextRunner.run(context -> {
            assertThat(context).hasSingleBean(OpenAPI.class);
            assertThat(context.getBean(OpenAPI.class).getInfo().getTitle())
                    .isEqualTo("Flow Mind Platform API");
            assertThat(context).hasSingleBean(GroupedOpenApi.class);
            assertThat(context).hasBean("platformGroupedOpenApi");
        });
    }

    @Test
    void knife4jDocsCanBeDisabled() {
        docsContextRunner
                .withPropertyValues("flow-mind.platform.docs.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OpenAPI.class);
                    assertThat(context).doesNotHaveBean(GroupedOpenApi.class);
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
                "Expense Instance", "biz-001", "u_sales_01", "业务员", "mock-dept",
                "[\"approve\"]", "{}", "RUNNING", "2026-07-23 09:00:00");
        jdbcTemplate.update("INSERT INTO process_active_task "
                        + "(id, instance_id, definition_id, node_code, candidate_user_ids, assignee_user_id, "
                        + "assignee_user_name, task_status, lock_version, created_at, due_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "task-001", "instance-001", "definition-001", "approve", "[\"u_sales_01\"]",
                "u_sales_01", "业务员", "ACTIVE", Long.valueOf(7L),
                "2026-07-23 09:10:00", "2026-07-24 09:10:00");
        jdbcTemplate.update("INSERT INTO process_history_task "
                        + "(id, instance_id, operation_id, active_task_id, node_code, assignee_user_id, "
                        + "assignee_user_name, handle_type, action_type, comment_text, variables_snapshot, "
                        + "started_at, completed_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "history-001", "instance-001", "operation-001", "task-history-001", "approve",
                "u_sales_01", "业务员", "NORMAL", "APPROVE", "approved", "{}",
                "2026-07-23 08:50:00", "2026-07-23 09:20:00");
    }

    private void insertTimeoutFixtures(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("INSERT INTO process_definition "
                        + "(id, process_code, process_name, system_code, version, definition_status, "
                        + "activation_status, gray_status, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "definition-timeout", "timeout", "Timeout", "oa", Integer.valueOf(1), "PUBLISHED",
                "ACTIVE", "OFF", "admin");
        jdbcTemplate.update("INSERT INTO process_node "
                        + "(id, definition_id, node_code, node_name, node_type, timeout_config, sort_order) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                "node-timeout", "definition-timeout", "review", "Review", "USER_TASK",
                "{\"enabled\":true,\"durationMinutes\":30,\"action\":\"JUMP\","
                        + "\"targetNodeCode\":\"fallback\"}",
                Integer.valueOf(1));
        jdbcTemplate.update("INSERT INTO process_instance "
                        + "(id, definition_id, process_code, process_name, version, instance_title, "
                        + "business_key, starter_user_id, starter_user_name, starter_dept_id, "
                        + "current_node_codes, variables_json, instance_status, started_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "instance-timeout", "definition-timeout", "timeout", "Timeout", Integer.valueOf(1),
                "Timeout instance", "biz-timeout", "mock-user", "Mock User", "mock-dept",
                "[\"review\"]", "{}", "RUNNING", "2026-07-29 09:00:00");
        jdbcTemplate.update("INSERT INTO process_active_task "
                        + "(id, instance_id, definition_id, node_code, candidate_user_ids, task_status, "
                        + "lock_version, created_at, due_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "task-timeout", "instance-timeout", "definition-timeout", "review", "[\"mock-user\"]",
                "ACTIVE", Long.valueOf(0L), "2026-07-29 09:10:00", "2026-07-29 10:00:00");
    }

    @Configuration
    static class CustomSpiConfiguration {
        @Bean
        FileStorageProvider customFileStorageProvider() {
            return new CustomFileStorageProvider();
        }

        @Bean
        MessagePublisher customMessagePublisher() {
            return new CustomMessagePublisher();
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
    static class RuntimeServiceConfiguration {
        @Bean
        AdminProcessService adminProcessService() {
            return mock(AdminProcessService.class);
        }

        @Bean
        ProcessRuntimeService processRuntimeService() {
            return mock(ProcessRuntimeService.class);
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

    @Configuration
    static class OrganizationProviderConfiguration {
        @Bean
        OrganizationProvider organizationProvider() {
            return new OrganizationProvider() {
                @Override
                public List<com.flowmind.platform.api.dto.DepartmentDTO> listDepartments() {
                    return java.util.Collections.emptyList();
                }

                @Override
                public List<com.flowmind.platform.api.dto.UserDTO> listUsersByDepartment(String departmentId) {
                    return java.util.Collections.emptyList();
                }

                @Override
                public List<com.flowmind.platform.api.dto.UserDTO> listUsersByRole(String roleCode) {
                    return java.util.Collections.emptyList();
                }

                @Override
                public List<com.flowmind.platform.api.dto.UserDTO> listUsersByRoleAndDepartment(String roleCode,
                                                                                                   String departmentId) {
                    return java.util.Collections.emptyList();
                }

                @Override
                public java.util.Optional<com.flowmind.platform.api.dto.UserDTO> findUser(String userId) {
                    return java.util.Optional.of(new com.flowmind.platform.api.dto.UserDTO(userId, "Host User"));
                }

                @Override
                public java.util.Optional<com.flowmind.platform.api.dto.DepartmentDTO> findDepartment(String departmentId) {
                    return java.util.Optional.empty();
                }
            };
        }
    }

    @Configuration
    static class CustomApproverResolverConfiguration {
        @Bean
        ApproverResolver customApproverResolver() {
            return new CustomApproverResolver();
        }
    }

    static class CustomApproverResolver implements ApproverResolver {
        @Override
        public List<com.flowmind.platform.api.dto.UserDTO> resolveApprovers(
                com.flowmind.platform.api.request.ApproverResolveRequest request) {
            return java.util.Collections.emptyList();
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

    static class CustomMessagePublisher implements MessagePublisher {
        @Override
        public void publish(com.flowmind.platform.api.dto.ProcessMessage message) {
        }
    }

    static class CustomTaskQueryService implements TaskQueryService {
        @Override
        public TaskDTO getTask(String taskId) {
            return null;
        }

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

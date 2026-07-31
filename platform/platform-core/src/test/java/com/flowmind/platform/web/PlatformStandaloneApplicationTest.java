package com.flowmind.platform.web;

import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.request.AttachmentAccessRequest;
import com.flowmind.platform.api.request.ApproverResolveRequest;
import com.flowmind.platform.api.service.CallbackService;
import com.flowmind.platform.api.service.ProcessDefinitionService;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import com.flowmind.platform.api.service.TaskQueryService;
import com.flowmind.platform.api.spi.AttachmentAccessProvider;
import com.flowmind.platform.api.spi.ApproverResolver;
import com.flowmind.platform.api.spi.CurrentUserProvider;
import com.flowmind.platform.api.spi.FileStorageProvider;
import com.flowmind.platform.api.spi.MessagePublisher;
import com.flowmind.platform.api.spi.OrganizationProvider;
import com.flowmind.platform.api.spi.WorkflowCallbackHandler;
import com.flowmind.platform.core.runtime.DefaultApproverResolver;
import com.flowmind.platform.mock.InMemoryOrganizationProvider;
import com.flowmind.platform.mock.MockAttachmentAccessProvider;
import com.flowmind.platform.mock.RecordingMessagePublisher;
import com.flowmind.platform.mock.RecordingWorkflowCallbackHandler;
import com.flowmind.platform.storage.LocalDiskFileStorageProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 独立运行入口契约测试。
 */
class PlatformStandaloneApplicationTest {

    @TempDir
    Path tempDir;

    @Test
    void standaloneApplicationShouldProvideSpringBootMainClass() {
        SpringBootApplication annotation = PlatformStandaloneApplication.class
                .getAnnotation(SpringBootApplication.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.scanBasePackages()).contains("com.flowmind.platform");
    }

    @Test
    void standaloneCurrentUserProviderShouldMatchFlowTestDefaultStarter() {
        CurrentUserProvider provider = new PlatformStandaloneConfiguration().currentUserProvider();

        UserContext currentUser = provider.getCurrentUser();

        assertThat(currentUser.getUserId()).isEqualTo("u_admin_01");
        assertThat(currentUser.getDepartmentId()).isEqualTo("mock-dept");
    }

    @Test
    void standaloneCurrentUserProviderShouldUseFlowTestUserHeader() {
        CurrentUserProvider provider = new PlatformStandaloneConfiguration().currentUserProvider();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Flow-User-Id", "u_dept_manager_01");

        try {
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            UserContext currentUser = provider.getCurrentUser();

            assertThat(currentUser.getUserId()).isEqualTo("u_dept_manager_01");
            assertThat(currentUser.getDepartmentId()).isEqualTo("dept_sales");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void standaloneApproverResolverShouldUseConfiguredUserIds() {
        PlatformStandaloneConfiguration configuration = new PlatformStandaloneConfiguration();
        ApproverResolver resolver = configuration.approverResolver(configuration.organizationProvider());
        ApproverResolveRequest request = new ApproverResolveRequest();
        Map<String, Object> config = new LinkedHashMap<String, Object>();
        config.put("userIds", Arrays.asList("u_dept_manager_01", "u_finance_01"));
        request.setApproverRuleType(ApproverRuleTypeEnum.USER);
        request.setApproverRuleConfig(config);

        List<UserDTO> users = resolver.resolveApprovers(request);

        assertThat(users).extracting("userId").containsExactly("u_dept_manager_01", "u_finance_01");
        assertThat(resolver).isInstanceOf(DefaultApproverResolver.class);
    }

    @Test
    void standaloneApproverResolverShouldResolveDepartmentManagerExpression() {
        PlatformStandaloneConfiguration configuration = new PlatformStandaloneConfiguration();
        ApproverResolver resolver = configuration.approverResolver(configuration.organizationProvider());

        ApproverResolveRequest salesRequest = expressionRequest("dept_sales");
        ApproverResolveRequest financeRequest = expressionRequest("dept_finance");

        assertThat(resolver.resolveApprovers(salesRequest)).extracting("userId")
                .containsExactly("u_dept_manager_01");
        assertThat(resolver.resolveApprovers(financeRequest)).extracting("userId")
                .containsExactly("u_dept_manager_02");
    }

    @Test
    void standaloneOrganizationProviderDoesNotResolveUnknownRoleOrDepartment() {
        PlatformStandaloneConfiguration configuration = new PlatformStandaloneConfiguration();
        OrganizationProvider provider = configuration.organizationProvider();

        assertThat(provider.listUsersByDepartment("unknown-department")).isEmpty();
        assertThat(provider.listUsersByRole("unknown-role")).isEmpty();

        ApproverResolver resolver = configuration.approverResolver(provider);
        ApproverResolveRequest request = new ApproverResolveRequest();
        request.setApproverRuleType(ApproverRuleTypeEnum.ROLE);
        request.setApproverRuleConfig(Collections.<String, Object>singletonMap("roleCode", "unknown-role"));
        assertThatThrownBy(() -> resolver.resolveApprovers(request))
                .hasMessageContaining("approver rule resolved no valid user");
    }

    private ApproverResolveRequest expressionRequest(String starterDeptId) {
        ApproverResolveRequest request = new ApproverResolveRequest();
        request.setApproverRuleType(ApproverRuleTypeEnum.APPROVER_EXPRESSION);
        request.setApproverRuleConfig(Collections.<String, Object>singletonMap(
                "expression", "departmentManager(starterDeptId)"));
        request.setStarterDeptId(starterDeptId);
        return request;
    }

    @Test
    void standaloneApplicationStartsWithPlatformServiceBeans() {
        String databasePath = tempDir.resolve("standalone.db").toAbsolutePath().toString().replace('\\', '/');

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(PlatformStandaloneApplication.class)
                .web(WebApplicationType.NONE)
                .properties("flow-mind.platform.sqlite.path=" + databasePath)
                .run()) {
            assertThat(context.getBeansOfType(CallbackController.class)).hasSize(1);
            assertThat(context.getBeansOfType(ProcessDefinitionController.class)).hasSize(1);
            assertThat(context.getBeansOfType(ProcessRuntimeController.class)).hasSize(1);
            assertThat(context.getBeansOfType(PlatformQueryController.class)).hasSize(1);
            assertThat(context.getBeansOfType(CallbackService.class)).hasSize(1);
            assertThat(context.getBeansOfType(ProcessDefinitionService.class)).hasSize(1);
            assertThat(context.getBeansOfType(ProcessRuntimeService.class)).hasSize(1);
            assertThat(context.getBeansOfType(TaskQueryService.class)).hasSize(1);
            assertThat(context.getBean(FileStorageProvider.class)).isInstanceOf(LocalDiskFileStorageProvider.class);
            assertThat(context.getBean(AttachmentAccessProvider.class)).isInstanceOf(MockAttachmentAccessProvider.class);
            assertThat(context.getBean(AttachmentAccessProvider.class)
                    .isAllowed(new AttachmentAccessRequest())).isTrue();
            assertThat(context.getBean(MessagePublisher.class)).isInstanceOf(RecordingMessagePublisher.class);
            assertThat(context.getBean(WorkflowCallbackHandler.class))
                    .isInstanceOf(RecordingWorkflowCallbackHandler.class);
            assertThat(context.getBean(OrganizationProvider.class)).isInstanceOf(InMemoryOrganizationProvider.class);
            assertThat(context.getBean(ApproverResolver.class)).isInstanceOf(DefaultApproverResolver.class);
            Integer operationRecordTables = context.getBean(JdbcTemplate.class).queryForObject(
                    "SELECT COUNT(1) FROM sqlite_master WHERE type = 'table' AND name = 'process_operation_record'",
                    Integer.class);
            assertThat(operationRecordTables).isEqualTo(Integer.valueOf(1));
            Integer busyTimeout = context.getBean(JdbcTemplate.class)
                    .queryForObject("PRAGMA busy_timeout", Integer.class);
            String journalMode = context.getBean(JdbcTemplate.class)
                    .queryForObject("PRAGMA journal_mode", String.class);
            assertThat(busyTimeout).isGreaterThanOrEqualTo(Integer.valueOf(10000));
            assertThat(journalMode).isEqualToIgnoringCase("wal");
        }
    }
}

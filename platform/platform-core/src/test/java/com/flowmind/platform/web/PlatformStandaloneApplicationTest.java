package com.flowmind.platform.web;

import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.request.ApproverResolveRequest;
import com.flowmind.platform.api.service.CallbackService;
import com.flowmind.platform.api.service.ProcessDefinitionService;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import com.flowmind.platform.api.service.TaskQueryService;
import com.flowmind.platform.api.spi.ApproverResolver;
import com.flowmind.platform.api.spi.CurrentUserProvider;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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

        assertThat(currentUser.getUserId()).isEqualTo("user_sales");
        assertThat(currentUser.getDepartmentId()).isEqualTo("dept_sales");
    }

    @Test
    void standaloneCurrentUserProviderShouldUseFlowTestUserHeader() {
        CurrentUserProvider provider = new PlatformStandaloneConfiguration().currentUserProvider();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Flow-User-Id", "user_manager");

        try {
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            UserContext currentUser = provider.getCurrentUser();

            assertThat(currentUser.getUserId()).isEqualTo("user_manager");
            assertThat(currentUser.getDepartmentId()).isEqualTo("dept_manager");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void standaloneApproverResolverShouldUseConfiguredUserIds() {
        ApproverResolver resolver = new PlatformStandaloneConfiguration().approverResolver();
        ApproverResolveRequest request = new ApproverResolveRequest();
        Map<String, Object> config = new LinkedHashMap<String, Object>();
        config.put("userIds", Arrays.asList("user_manager", "user_finance"));
        request.setApproverRuleType(ApproverRuleTypeEnum.USER);
        request.setApproverRuleConfig(config);

        List<UserDTO> users = resolver.resolveApprovers(request);

        assertThat(users).extracting("userId").containsExactly("user_manager", "user_finance");
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

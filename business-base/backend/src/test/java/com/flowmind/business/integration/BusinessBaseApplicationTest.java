package com.flowmind.business.integration;

import com.flowmind.business.BusinessBaseApplication;
import com.flowmind.business.message.BusinessAlertRecordRepository;
import com.flowmind.business.security.CurrentBusinessUserProvider;
import com.flowmind.business.security.PlatformCurrentUserAdapter;
import com.flowmind.platform.api.service.ReadRecordService;
import com.flowmind.platform.api.service.TaskQueryService;
import com.flowmind.platform.api.spi.CurrentUserProvider;
import com.flowmind.platform.persistence.repository.AlertRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BusinessBaseApplication.class,
        properties = "flow-mind.platform.sqlite.path=./target/b1-context-test.db")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BusinessBaseApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void starterAndTrustedIdentityAreWiredWithoutPlatformWebLayer() throws Exception {
        assertThat(context.getBean(TaskQueryService.class)).isNotNull();
        assertThat(context.getBean(ReadRecordService.class)).isNotNull();
        assertThat(context.getBean(AlertRecordRepository.class)).isInstanceOf(BusinessAlertRecordRepository.class);
        assertThat(context.getBean(CurrentBusinessUserProvider.class)).isNotNull();
        CurrentUserProvider platformUser = context.getBean(CurrentUserProvider.class);
        assertThat(platformUser).isInstanceOf(PlatformCurrentUserAdapter.class);
        MockHttpSession session = login();
        mockMvc.perform(get("/api/workflow/me").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value("u_sales_01"));

        Map<String, Object> beans = context.getBeansOfType(Object.class);
        assertThat(beans.values()).noneMatch(bean -> bean.getClass().getName()
                .startsWith("com.flowmind.platform.web."));
    }

    @Test
    void workflowUserSearchReturnsActiveUserCandidates() throws Exception {
        mockMvc.perform(get("/api/workflow/users")
                        .session(login())
                        .param("keyword", "u_finance")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("u_finance_01"))
                .andExpect(jsonPath("$[0].userName").value("王五"))
                .andExpect(jsonPath("$[0].departmentName").value("财务部"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void platformDebugRoutesAndStaticPageAreNotExposed() throws Exception {
        mockMvc.perform(get("/api/platform/tasks/todo").session(login())).andExpect(status().isNotFound());
        mockMvc.perform(get("/flow-test/index.html")).andExpect(status().isNotFound());
    }

    @Test
    void agentPlatformBridgeRequiresAnAdministratorSession() throws Exception {
        mockMvc.perform(get("/api/platform/definitions?pageNo=1&pageSize=10")
                        .session(login("sales01")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BUSINESS_ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("仅 agent-web 管理员桥接接口可以写入 business-base 内嵌平台库"));

        mockMvc.perform(get("/api/platform/definitions?pageNo=1&pageSize=10")
                .session(login("admin01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records").isArray());
    }

    @Test
    void adminConsoleApisRequireAuthenticationAndAdministratorRole() throws Exception {
        mockMvc.perform(get("/api/admin/process-definitions?pageNo=1&pageSize=10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BUSINESS_AUTHENTICATION_REQUIRED"));

        mockMvc.perform(get("/api/admin/process-definitions?pageNo=1&pageSize=10")
                        .session(login("sales01")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BUSINESS_ACCESS_DENIED"));

        mockMvc.perform(get("/api/admin/process-definitions?pageNo=1&pageSize=10")
                        .session(login("admin01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records").isArray());

        mockMvc.perform(get("/api/admin/process-instances?pageNo=1&pageSize=10")
                        .session(login("admin01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records").isArray());

        mockMvc.perform(get("/api/admin/business-entry-configs"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/business-entry-configs").session(login("sales01")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/business-entry-configs").session(login("admin01")))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
    }

    @Test
    void businessHallAndGenericStartApisRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/workflow/process-entry-links"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/workflow/processes/entry_application/start-context"))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpSession login() throws Exception {
        return login("sales01");
    }

    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}

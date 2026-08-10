package com.flowmind.business.integration;

import com.flowmind.business.BusinessBaseApplication;
import com.flowmind.business.security.CurrentBusinessUserProvider;
import com.flowmind.business.security.PlatformCurrentUserAdapter;
import com.flowmind.platform.api.service.ReadRecordService;
import com.flowmind.platform.api.service.TaskQueryService;
import com.flowmind.platform.api.spi.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void starterAndTrustedIdentityAreWiredWithoutPlatformWebLayer() {
        assertThat(context.getBean(TaskQueryService.class)).isNotNull();
        assertThat(context.getBean(ReadRecordService.class)).isNotNull();
        assertThat(context.getBean(CurrentBusinessUserProvider.class)).isNotNull();
        CurrentUserProvider platformUser = context.getBean(CurrentUserProvider.class);
        assertThat(platformUser).isInstanceOf(PlatformCurrentUserAdapter.class);
        assertThat(platformUser.getCurrentUser().getUserId()).isEqualTo("u_sales_01");

        Map<String, Object> beans = context.getBeansOfType(Object.class);
        assertThat(beans.values()).noneMatch(bean -> bean.getClass().getName()
                .startsWith("com.flowmind.platform.web."));
    }

    @Test
    void platformDebugRoutesAndStaticPageAreNotExposed() throws Exception {
        mockMvc.perform(get("/api/platform/tasks/todo")).andExpect(status().isNotFound());
        mockMvc.perform(get("/flow-test/index.html")).andExpect(status().isNotFound());
    }
}

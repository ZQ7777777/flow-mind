package com.flowmind.business.common;

import com.flowmind.business.security.BusinessAuthenticationException;
import com.flowmind.platform.api.error.PlatformError;
import com.flowmind.platform.api.error.PlatformErrorCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BusinessWebInfrastructureTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new BusinessExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void requestIdIsPropagatedToHeaderAndErrorBody() throws Exception {
        mockMvc.perform(get("/test/auth").header("X-Request-Id", "request-123")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "request-123"))
                .andExpect(jsonPath("$.requestId").value("request-123"))
                .andExpect(jsonPath("$.code").value("BUSINESS_AUTHENTICATION_REQUIRED"));
    }

    @Test
    void unsafeRequestIdIsReplaced() throws Exception {
        mockMvc.perform(get("/test/auth").header("X-Request-Id", "bad request id"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void publicPlatformErrorMapsWithoutCoreExceptionDependency() throws Exception {
        mockMvc.perform(get("/test/platform"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FLOW_TEST_CONFLICT"))
                .andExpect(jsonPath("$.details").isMap());
    }

    @RestController
    static class FailureController {
        @GetMapping("/test/auth")
        String authentication() {
            throw new BusinessAuthenticationException("需要登录");
        }

        @GetMapping("/test/platform")
        String platform() {
            throw new PublicPlatformException();
        }
    }

    static class PublicPlatformException extends RuntimeException implements PlatformError {
        @Override
        public String getErrorCode() {
            return "FLOW_TEST_CONFLICT";
        }

        @Override
        public PlatformErrorCategory getErrorCategory() {
            return PlatformErrorCategory.CONFLICT;
        }
    }
}

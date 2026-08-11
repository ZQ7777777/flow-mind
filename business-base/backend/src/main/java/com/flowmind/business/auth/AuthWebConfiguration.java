package com.flowmind.business.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile({"local", "test"})
public class AuthWebConfiguration implements WebMvcConfigurer {

    private final SessionAuthenticationInterceptor interceptor;
    private final AgentPlatformBridgeAdministratorInterceptor agentPlatformBridgeAdministratorInterceptor;

    public AuthWebConfiguration(SessionAuthenticationInterceptor interceptor,
                                AgentPlatformBridgeAdministratorInterceptor agentPlatformBridgeAdministratorInterceptor) {
        this.interceptor = interceptor;
        this.agentPlatformBridgeAdministratorInterceptor = agentPlatformBridgeAdministratorInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/auth/logout");
        registry.addInterceptor(agentPlatformBridgeAdministratorInterceptor)
                .addPathPatterns("/api/platform/definitions/**", "/api/platform/attachment-templates/**");
    }
}

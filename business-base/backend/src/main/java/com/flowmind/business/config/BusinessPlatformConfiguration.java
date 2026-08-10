package com.flowmind.business.config;

import com.flowmind.business.security.BusinessAuthorizationProvider;
import com.flowmind.business.security.CurrentBusinessUserProvider;
import com.flowmind.business.security.PlatformCurrentUserAdapter;
import com.flowmind.platform.api.spi.CurrentUserProvider;
import com.flowmind.platform.api.spi.OrganizationProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 业务身份与平台 Starter 的装配边界。
 *
 * @author FlowMind
 * @since 2026-08-10
 */
@Configuration
@EnableConfigurationProperties(BusinessBaseProperties.class)
public class BusinessPlatformConfiguration {

    @Bean
    @ConditionalOnBean(CurrentBusinessUserProvider.class)
    @ConditionalOnMissingBean(CurrentUserProvider.class)
    public CurrentUserProvider platformCurrentUserAdapter(CurrentBusinessUserProvider businessUserProvider,
                                                          ObjectProvider<OrganizationProvider> organizationProvider) {
        return new PlatformCurrentUserAdapter(businessUserProvider, organizationProvider);
    }

    @Bean
    @ConditionalOnMissingBean(BusinessAuthorizationProvider.class)
    public BusinessAuthorizationProvider businessAuthorizationProvider() {
        return userId -> false;
    }
}

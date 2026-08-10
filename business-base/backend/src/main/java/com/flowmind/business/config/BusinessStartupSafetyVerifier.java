package com.flowmind.business.config;

import com.flowmind.business.security.CurrentBusinessUserProvider;
import com.flowmind.business.security.local.LocalBusinessUserProvider;
import com.flowmind.platform.starter.properties.PlatformProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 阻止本地 Mock 身份和平台 Mock 被误带入共享或生产环境。
 *
 * @author FlowMind
 * @since 2026-08-10
 */
@Component
public class BusinessStartupSafetyVerifier implements SmartInitializingSingleton {

    private final Environment environment;
    private final PlatformProperties platformProperties;
    private final ObjectProvider<CurrentBusinessUserProvider> businessUserProvider;

    public BusinessStartupSafetyVerifier(Environment environment,
                                         PlatformProperties platformProperties,
                                         ObjectProvider<CurrentBusinessUserProvider> businessUserProvider) {
        this.environment = environment;
        this.platformProperties = platformProperties;
        this.businessUserProvider = businessUserProvider;
    }

    @Override
    public void afterSingletonsInstantiated() {
        boolean productionProfile = hasProfile("prod") || hasProfile("production");
        boolean developmentProfile = !productionProfile && (hasProfile("local") || hasProfile("test"));
        CurrentBusinessUserProvider provider = businessUserProvider.getIfAvailable();
        if (provider == null) {
            throw new IllegalStateException("CurrentBusinessUserProvider bean is required");
        }
        if (!developmentProfile && platformProperties.getMock().isEnabled()) {
            throw new IllegalStateException("platform mock SPI is allowed only in local/test profiles");
        }
        if (!developmentProfile && provider instanceof LocalBusinessUserProvider) {
            throw new IllegalStateException("local business user is allowed only in local/test profiles");
        }
        if (developmentProfile && !isLoopback(environment.getProperty("server.address", "127.0.0.1"))) {
            throw new IllegalStateException("local/test identity requires a loopback server address");
        }
    }

    private boolean hasProfile(String profile) {
        return Arrays.asList(environment.getActiveProfiles()).contains(profile);
    }

    private boolean isLoopback(String address) {
        return "127.0.0.1".equals(address) || "localhost".equalsIgnoreCase(address)
                || "::1".equals(address) || "0:0:0:0:0:0:0:1".equals(address);
    }
}

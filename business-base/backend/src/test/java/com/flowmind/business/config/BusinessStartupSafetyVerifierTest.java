package com.flowmind.business.config;

import com.flowmind.business.security.CurrentBusinessUserProvider;
import com.flowmind.platform.starter.properties.PlatformProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BusinessStartupSafetyVerifierTest {

    @Test
    void productionRejectsPlatformMocks() {
        PlatformProperties properties = new PlatformProperties();
        properties.getMock().setEnabled(true);

        assertThrows(IllegalStateException.class,
                () -> verifier(new MockEnvironment().withProperty("server.address", "127.0.0.1"), properties)
                        .afterSingletonsInstantiated());
    }

    @Test
    void productionAcceptsHostIdentityWithMocksDisabled() {
        PlatformProperties properties = new PlatformProperties();
        properties.getMock().setEnabled(false);

        assertDoesNotThrow(() -> verifier(new MockEnvironment(), properties).afterSingletonsInstantiated());
    }

    @Test
    void localProfileRequiresLoopbackBinding() {
        PlatformProperties properties = new PlatformProperties();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("server.address", "0.0.0.0");
        environment.setActiveProfiles("local");

        assertThrows(IllegalStateException.class,
                () -> verifier(environment, properties).afterSingletonsInstantiated());
    }

    @Test
    void productionProfileCannotBeCombinedWithLocalProfile() {
        PlatformProperties properties = new PlatformProperties();
        properties.getMock().setEnabled(true);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod", "local");

        assertThrows(IllegalStateException.class,
                () -> verifier(environment, properties).afterSingletonsInstantiated());
    }

    private BusinessStartupSafetyVerifier verifier(MockEnvironment environment,
                                                   PlatformProperties properties) {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        CurrentBusinessUserProvider provider = () ->
                new CurrentBusinessUserProvider.BusinessUser("host-user", "host-dept");
        beans.registerSingleton("businessUserProvider", provider);
        return new BusinessStartupSafetyVerifier(environment, properties,
                beans.getBeanProvider(CurrentBusinessUserProvider.class));
    }
}

package com.flowmind.business.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimeoutScanConfigurationTest {

    @Test
    void enablesAutomaticTimeoutScanInRuntimeProfiles() throws IOException {
        assertThat(property("application.yml", "flow-mind.platform.timeout-scan.enabled")).isEqualTo(Boolean.TRUE);
        assertThat(property("application-local.yml", "flow-mind.platform.timeout-scan.enabled")).isEqualTo(Boolean.TRUE);
    }

    private Object property(String resource, String name) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load(resource, new ClassPathResource(resource));
        return sources.get(0).getProperty(name);
    }
}

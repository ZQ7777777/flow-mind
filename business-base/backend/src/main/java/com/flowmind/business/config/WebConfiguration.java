package com.flowmind.business.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 业务 Web 层隔离配置。
 *
 * @author FlowMind
 * @since 2026-08-10
 */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/flow-test/**")
                .addResourceLocations("classpath:/business-disabled-flow-test/");
    }
}

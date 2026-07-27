package com.flowmind.platform.starter;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j/OpenAPI 文档自动配置。
 *
 * <p>保持为独立自动配置类，使未引入 Web MVC 的平台核心使用方不会被文档依赖阻断。</p>
 *
 * @author FlowMind
 * @since 2026-07-27
 */
@Configuration
@ConditionalOnClass({OpenAPI.class, GroupedOpenApi.class})
@ConditionalOnProperty(prefix = "flow-mind.platform.docs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PlatformKnife4jAutoConfiguration {

    /** 平台 API 的基础 OpenAPI 元数据。 */
    @Bean
    @ConditionalOnMissingBean
    public OpenAPI platformOpenApi() {
        return new OpenAPI().info(new Info().title("Flow Mind Platform API").version("v1"));
    }

    /** 平台接口的默认文档分组。 */
    @Bean(name = "platformGroupedOpenApi")
    @ConditionalOnMissingBean(name = "platformGroupedOpenApi")
    public GroupedOpenApi platformGroupedOpenApi() {
        return GroupedOpenApi.builder().group("flow-mind-platform")
                .pathsToMatch("/api/**").build();
    }
}

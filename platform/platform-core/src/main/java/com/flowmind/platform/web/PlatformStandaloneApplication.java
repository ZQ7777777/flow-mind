package com.flowmind.platform.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 流程平台本地独立运行入口，用于加载静态调试页面和后续 REST 调试适配。
 *
 * @author FlowMind
 * @since 2026-07-23
 */
@SpringBootApplication(scanBasePackages = "com.flowmind.platform")
public class PlatformStandaloneApplication {

    /**
     * 启动本地独立 Spring Boot 应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(PlatformStandaloneApplication.class, args);
    }
}

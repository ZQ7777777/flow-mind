package com.flowmind.business;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Flow Mind 业务系统基础底座启动入口。
 *
 * @author FlowMind
 * @since 2026-08-10
 */
@SpringBootApplication(scanBasePackages = "com.flowmind.business")
public class BusinessBaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(BusinessBaseApplication.class, args);
    }
}

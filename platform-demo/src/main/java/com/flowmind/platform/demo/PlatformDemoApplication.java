package com.flowmind.platform.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.flowmind.platform")
public class PlatformDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformDemoApplication.class, args);
    }
}


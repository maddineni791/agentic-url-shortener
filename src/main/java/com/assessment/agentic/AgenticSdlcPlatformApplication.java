package com.assessment.agentic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AgenticSdlcPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgenticSdlcPlatformApplication.class, args);
    }
}

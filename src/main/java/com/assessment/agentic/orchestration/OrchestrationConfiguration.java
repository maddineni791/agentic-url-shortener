package com.assessment.agentic.orchestration;

import java.util.concurrent.Executor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(OrchestrationProperties.class)
public class OrchestrationConfiguration {

    @Bean(name = "orchestrationExecutor")
    Executor orchestrationExecutor(OrchestrationProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getExecutorPoolSize());
        executor.setMaxPoolSize(properties.getExecutorPoolSize());
        executor.setQueueCapacity(properties.getExecutorQueueCapacity());
        executor.setThreadNamePrefix("wf-orchestrator-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean(name = "taskGraphPool")
    Executor taskGraphPool(OrchestrationProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(2, properties.getExecutorPoolSize()));
        executor.setMaxPoolSize(Math.max(2, properties.getExecutorPoolSize()));
        executor.setQueueCapacity(properties.getExecutorQueueCapacity());
        executor.setThreadNamePrefix("wf-task-graph-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}

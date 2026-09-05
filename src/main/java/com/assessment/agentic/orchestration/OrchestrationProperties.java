package com.assessment.agentic.orchestration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for how the orchestrator runs a revision and how interrupted revisions are recovered.
 *
 * <p>{@code async=false} (the default) keeps submission synchronous so deterministic evaluation is
 * reproducible. Production deployments set {@code async=true} so a submission returns immediately and
 * the graph runs on the bounded {@code orchestrationExecutor}; restart recovery then re-drives any
 * revision left {@code RUNNING} by an instance that stopped mid-flight.
 */
@ConfigurationProperties(prefix = "agentic.orchestration")
public class OrchestrationProperties {

    private boolean async = false;
    private boolean recoveryScheduled = true;
    private Duration recoveryInterval = Duration.ofSeconds(30);
    private int recoveryBatchSize = 20;
    private int executorPoolSize = 4;
    private int executorQueueCapacity = 64;

    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    public boolean isRecoveryScheduled() {
        return recoveryScheduled;
    }

    public void setRecoveryScheduled(boolean recoveryScheduled) {
        this.recoveryScheduled = recoveryScheduled;
    }

    public Duration getRecoveryInterval() {
        return recoveryInterval;
    }

    public void setRecoveryInterval(Duration recoveryInterval) {
        this.recoveryInterval = recoveryInterval;
    }

    public int getRecoveryBatchSize() {
        return recoveryBatchSize;
    }

    public void setRecoveryBatchSize(int recoveryBatchSize) {
        this.recoveryBatchSize = recoveryBatchSize;
    }

    public int getExecutorPoolSize() {
        return executorPoolSize;
    }

    public void setExecutorPoolSize(int executorPoolSize) {
        this.executorPoolSize = executorPoolSize;
    }

    public int getExecutorQueueCapacity() {
        return executorQueueCapacity;
    }

    public void setExecutorQueueCapacity(int executorQueueCapacity) {
        this.executorQueueCapacity = executorQueueCapacity;
    }
}

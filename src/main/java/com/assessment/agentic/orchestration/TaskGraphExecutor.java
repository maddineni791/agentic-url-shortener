package com.assessment.agentic.orchestration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * Executes a declared task graph wave by wave. Each wave is the set of not-yet-run nodes whose
 * dependencies have all completed; a wave with more than one node is run concurrently on the
 * bounded {@code taskGraphExecutor} pool, which is the synchronization barrier for the next wave.
 */
@Component
public class TaskGraphExecutor {

    /** A node in the lifecycle graph: a task key plus the keys it depends on. */
    public record GraphNode(String key, List<String> dependsOn) {
        public GraphNode {
            dependsOn = List.copyOf(dependsOn);
        }
    }

    private final Executor executor;

    public TaskGraphExecutor(@org.springframework.beans.factory.annotation.Qualifier("taskGraphPool") Executor executor) {
        this.executor = executor;
    }

    /**
     * @return the ordered list of waves that were executed, each wave listing the task keys it contained
     *         (waves of size &gt; 1 ran in parallel).
     */
    public List<List<String>> execute(List<GraphNode> nodes, Consumer<String> runNode) {
        Set<String> completed = new HashSet<>();
        List<List<String>> waves = new ArrayList<>();
        while (completed.size() < nodes.size()) {
            List<GraphNode> wave = nodes.stream()
                .filter(node -> !completed.contains(node.key()))
                .filter(node -> completed.containsAll(node.dependsOn()))
                .toList();
            if (wave.isEmpty()) {
                throw new IllegalStateException("Task graph has a cycle or an unsatisfiable dependency.");
            }
            List<String> waveKeys = wave.stream().map(GraphNode::key).toList();
            if (wave.size() == 1) {
                runNode.accept(waveKeys.get(0));
            } else {
                CompletableFuture<?>[] futures = wave.stream()
                    .map(node -> CompletableFuture.runAsync(() -> runNode.accept(node.key()), executor))
                    .toArray(CompletableFuture[]::new);
                try {
                    CompletableFuture.allOf(futures).join();
                } catch (java.util.concurrent.CompletionException completion) {
                    Throwable cause = completion.getCause();
                    if (cause instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    throw completion;
                }
            }
            completed.addAll(waveKeys);
            waves.add(waveKeys);
        }
        return waves;
    }
}

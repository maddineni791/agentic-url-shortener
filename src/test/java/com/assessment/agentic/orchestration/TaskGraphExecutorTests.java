package com.assessment.agentic.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class TaskGraphExecutorTests {

    private final TaskGraphExecutor executor = new TaskGraphExecutor(Executors.newFixedThreadPool(3));

    @Test
    void runsNodesInDependencyOrderAndGroupsParallelWaves() {
        List<TaskGraphExecutor.GraphNode> graph = List.of(
            new TaskGraphExecutor.GraphNode("design", List.of()),
            new TaskGraphExecutor.GraphNode("impl", List.of("design")),
            new TaskGraphExecutor.GraphNode("tests", List.of("design")),
            new TaskGraphExecutor.GraphNode("security", List.of("design")),
            new TaskGraphExecutor.GraphNode("apply", List.of("impl", "tests")),
            new TaskGraphExecutor.GraphNode("release", List.of("apply", "security"))
        );
        ConcurrentLinkedQueue<String> order = new ConcurrentLinkedQueue<>();

        List<List<String>> waves = executor.execute(graph, order::add);

        assertThat(waves.get(0)).containsExactly("design");
        assertThat(waves.get(1)).containsExactlyInAnyOrder("impl", "tests", "security");
        assertThat(waves.get(2)).containsExactly("apply");
        assertThat(waves.get(3)).containsExactly("release");
        assertThat(order).hasSize(6);
        assertThat(order).startsWith("design");
        assertThat(order).endsWith("release");
    }

    @Test
    void propagatesNodeFailure() {
        List<TaskGraphExecutor.GraphNode> graph = List.of(
            new TaskGraphExecutor.GraphNode("a", List.of()),
            new TaskGraphExecutor.GraphNode("b", List.of("a"))
        );
        assertThatThrownBy(() -> executor.execute(graph, key -> {
            if (key.equals("a")) {
                throw new IllegalStateException("node a failed");
            }
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("node a failed");
    }

    @Test
    void rejectsUnsatisfiableGraph() {
        List<TaskGraphExecutor.GraphNode> graph = List.of(
            new TaskGraphExecutor.GraphNode("x", List.of("missing"))
        );
        assertThatThrownBy(() -> executor.execute(graph, key -> { }))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cycle or an unsatisfiable dependency");
    }
}

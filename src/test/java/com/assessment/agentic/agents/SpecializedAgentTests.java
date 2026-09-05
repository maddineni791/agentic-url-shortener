package com.assessment.agentic.agents;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SpecializedAgentTests {

    @Autowired
    private RequirementUnderstandingAgent requirementUnderstandingAgent;

    @Autowired
    private AmbiguityClarificationAgent ambiguityClarificationAgent;

    @Autowired
    private TaskDecompositionAgent taskDecompositionAgent;

    @Autowired
    private ImplementationAgent implementationAgent;

    @Autowired
    private TestGenerationAgent testGenerationAgent;

    @Autowired
    private SecurityRiskReviewAgent securityRiskReviewAgent;

    @Autowired
    private ReleaseReadinessAgent releaseReadinessAgent;

    @Test
    void requirementAgentProducesValidatedRequirementArtifact() {
        ExecutionContext context = context("""
            Build a URL shortener API with create endpoint, redirect behavior, PostgreSQL persistence,
            rate limiting, blocked hosts, expiry, retention, and UTC analytics.
            """);
        AgentTask task = task("understand", AgentRole.REQUIREMENT_INTERPRETER, "Normalize the submitted requirement");

        AgentExecutionResult<RequirementAnalysis> result = requirementUnderstandingAgent.execute(task, context);

        assertThat(result.output().acceptanceCriteria()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(result.artifacts()).singleElement()
            .satisfies(artifact -> {
                assertThat(artifact.name()).isEqualTo("normalized-requirement.json");
                assertThat(artifact.sha256()).hasSize(64);
            });
        assertThat(result.modelResult().schemaName()).isEqualTo("requirement-analysis-v1");
    }

    @Test
    void ambiguityAgentBlocksMateriallyUnderspecifiedRequirement() {
        ExecutionContext context = context("Make it better.");
        AgentTask task = task("ambiguity", AgentRole.AMBIGUITY_ANALYST, "Decide whether implementation may start");

        AgentExecutionResult<AmbiguityAnalysis> result = ambiguityClarificationAgent.execute(task, context);

        assertThat(result.output().requiresClarification()).isTrue();
        assertThat(result.output().blockingFields()).contains("apiBehavior", "persistenceRequirements", "securityRequirements", "timeBoundaries");
        assertThat(result.output().questions()).isNotEmpty();
    }

    @Test
    void ambiguityAgentAllowsConcreteRequirementWithoutScenarioEnum() {
        ExecutionContext context = context("""
            Add a URL creation API and redirect endpoint with PostgreSQL storage, rate limiting,
            blocked host validation, expiry, retention cleanup, and UTC daily analytics.
            """);
        AgentTask task = task("ambiguity", AgentRole.AMBIGUITY_ANALYST, "Decide whether implementation may start");

        AgentExecutionResult<AmbiguityAnalysis> result = ambiguityClarificationAgent.execute(task, context);

        assertThat(result.output().requiresClarification()).isFalse();
        assertThat(result.output().blockingFields()).isEmpty();
    }

    @Test
    void plannerProducesDynamicExecutionPlaneTasksWithDependenciesAndParallelBranches() {
        ExecutionContext context = context("Add total redirect analytics to the URL shortener.");
        AgentTask task = task("plan", AgentRole.PLANNER, "Decompose engineering work");

        AgentExecutionResult<TaskPlan> result = taskDecompositionAgent.execute(task, context);

        assertThat(result.output().tasks())
            .extracting(TaskPlan.PlannedTask::id)
            .contains("analyze-repository", "design-change", "implement-change", "generate-tests", "security-risk-review", "release-readiness");
        assertThat(result.output().tasks())
            .anySatisfy(planned -> {
                assertThat(planned.id()).isEqualTo("synchronize-patch");
                assertThat(planned.dependencies()).containsExactlyInAnyOrder("implement-change", "generate-tests");
            });
        assertThat(result.output().parallelGroups()).isNotEmpty();
    }

    @Test
    void implementationAndTestAgentsProduceStructuredFileOperationProposals() {
        ExecutionContext context = context("Create a runnable URL shortener vertical slice.");

        AgentExecutionResult<FileOperationProposalSet> implementation = implementationAgent.execute(
            task("implement-change", AgentRole.IMPLEMENTER, "Generate production code"), context);
        AgentExecutionResult<FileOperationProposalSet> tests = testGenerationAgent.execute(
            task("generate-tests", AgentRole.TEST_ENGINEER, "Generate tests"), context);

        assertThat(implementation.output().fileOperations()).singleElement()
            .satisfies(operation -> {
                assertThat(operation.operationType()).isEqualTo("CREATE");
                assertThat(operation.normalizedRelativePath()).startsWith("src/main/java/");
                assertThat(operation.completeProposedContent()).contains("GeneratedUrlShortenerSlice");
                assertThat(operation.taskId()).isEqualTo("implement-change");
            });
        assertThat(tests.output().fileOperations()).singleElement()
            .satisfies(operation -> {
                assertThat(operation.normalizedRelativePath()).startsWith("src/test/java/");
                assertThat(operation.completeProposedContent()).contains("@Test");
            });
    }

    @Test
    void governanceAgentsProduceRiskAndReleaseArtifacts() {
        ExecutionContext context = context("Review generated URL shortener artifacts.");

        AgentExecutionResult<SecurityRiskReview> risk = securityRiskReviewAgent.execute(
            task("security-risk-review", AgentRole.SECURITY_REVIEWER, "Review risk"), context);
        AgentExecutionResult<ReleaseReadiness> release = releaseReadinessAgent.execute(
            task("release-readiness", AgentRole.RELEASE_REVIEWER, "Review release readiness"), context);

        assertThat(risk.artifacts()).singleElement().satisfies(artifact -> assertThat(artifact.name()).isEqualTo("security-risk-review.json"));
        assertThat(release.artifacts()).singleElement().satisfies(artifact -> assertThat(artifact.name()).isEqualTo("release-readiness.json"));
        assertThat(release.output().outcome()).isNotBlank();
    }

    private ExecutionContext context(String requirement) {
        return new ExecutionContext("wf-test", 1, requirement, Map.of());
    }

    private AgentTask task(String id, AgentRole role, String goal) {
        return new AgentTask(id, role, goal, List.of(), Map.of());
    }
}

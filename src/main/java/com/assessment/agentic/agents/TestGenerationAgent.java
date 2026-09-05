package com.assessment.agentic.agents;

import com.assessment.agentic.model.ModelCapability;
import com.assessment.agentic.model.ModelGateway;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TestGenerationAgent extends BaseModelAgent<FileOperationProposalSet> {

    public TestGenerationAgent(ModelGateway modelGateway, Validator validator) {
        super(modelGateway, validator, AgentRole.TEST_ENGINEER, ModelCapability.TEST_GENERATION,
            "file-operation-proposal-v1", AgentSchemas.proposal(), "test-proposal.json", "FILE_OPERATIONS");
    }

    @Override
    protected FileOperationProposalSet map(AgentTask task, ExecutionContext context, Map<String, String> fields) {
        String path = "src/test/java/com/assessment/generated/urlshortener/GeneratedUrlShortenerSliceTests.java";
        FileOperationProposal operation = new FileOperationProposal(
            "CREATE",
            path,
            """
            package com.assessment.generated.urlshortener;

            import static org.assertj.core.api.Assertions.assertThat;

            import org.junit.jupiter.api.Test;

            class GeneratedUrlShortenerSliceTests {
                @Test
                void exposesGeneratedUrlShortenerBehavior() {
                    assertThat(GeneratedUrlShortenerSlice.behavior()).contains("create-url", "redirect", "analytics");
                }
            }
            """,
            null,
            "Create a deterministic test proposal for the generated URL-shortener production slice.",
            "REQ-005",
            task.id(),
            context.workflowId() + "/revision-" + context.revision() + "/" + task.id()
        );
        return new FileOperationProposalSet(List.of(operation), List.of(path), List.of(fields.get("lineage")));
    }
}

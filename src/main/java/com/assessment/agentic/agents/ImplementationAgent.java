package com.assessment.agentic.agents;

import com.assessment.agentic.model.ModelCapability;
import com.assessment.agentic.model.ModelGateway;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ImplementationAgent extends BaseModelAgent<FileOperationProposalSet> {

    public ImplementationAgent(ModelGateway modelGateway, Validator validator) {
        super(modelGateway, validator, AgentRole.IMPLEMENTER, ModelCapability.IMPLEMENTATION,
            "file-operation-proposal-v1", AgentSchemas.proposal(), "implementation-proposal.json", "FILE_OPERATIONS");
    }

    @Override
    protected FileOperationProposalSet map(AgentTask task, ExecutionContext context, Map<String, String> fields) {
        String path = "src/main/java/com/assessment/generated/urlshortener/GeneratedUrlShortenerSlice.java";
        FileOperationProposal operation = new FileOperationProposal(
            "CREATE",
            path,
            """
            package com.assessment.generated.urlshortener;

            public final class GeneratedUrlShortenerSlice {
                private GeneratedUrlShortenerSlice() {
                }

                public static String behavior() {
                    return "create-url redirect analytics";
                }
            }
            """,
            null,
            "Create a bounded deterministic production slice for the requested URL-shortener behavior.",
            "REQ-005",
            task.id(),
            context.workflowId() + "/revision-" + context.revision() + "/" + task.id()
        );
        return new FileOperationProposalSet(List.of(operation), List.of(path), List.of(fields.get("lineage")));
    }
}

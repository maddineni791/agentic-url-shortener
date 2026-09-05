package com.assessment.agentic.agents;

import com.assessment.agentic.model.ModelCapability;
import com.assessment.agentic.model.ModelGateway;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RepairAgent extends BaseModelAgent<FileOperationProposalSet> {

    public RepairAgent(ModelGateway modelGateway, Validator validator) {
        super(modelGateway, validator, AgentRole.REPAIR_ENGINEER, ModelCapability.REPAIR,
            "file-operation-proposal-v1", AgentSchemas.proposal(), "repair-proposal.json", "FILE_OPERATIONS");
    }

    @Override
    protected FileOperationProposalSet map(AgentTask task, ExecutionContext context, Map<String, String> fields) {
        String path = task.inputs().getOrDefault("failedPath", "src/main/java/com/assessment/generated/urlshortener/GeneratedUrlShortenerSlice.java");
        FileOperationProposal operation = new FileOperationProposal(
            "UPDATE",
            path,
            task.inputs().getOrDefault("correctedContent", ImplementationAgent.workingContent()),
            task.inputs().get("expectedCurrentSha256"),
            "Repair the failed generated artifact using validation evidence.",
            "REQ-006",
            task.id(),
            context.workflowId() + "/revision-" + context.revision() + "/" + task.id()
        );
        List<String> lineage = new ArrayList<>(splitList(fields.get("lineage")));
        lineage.add(task.inputs().getOrDefault("failureClass", "UNKNOWN"));
        return new FileOperationProposalSet(List.of(operation), List.of(path), lineage);
    }
}

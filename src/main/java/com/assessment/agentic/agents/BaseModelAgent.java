package com.assessment.agentic.agents;

import com.assessment.agentic.model.ModelCapability;
import com.assessment.agentic.model.ModelGateway;
import com.assessment.agentic.model.ModelRequest;
import com.assessment.agentic.model.ModelResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class BaseModelAgent<T> implements AgentExecutor<T> {

    private final ModelGateway modelGateway;
    private final Validator validator;
    private final AgentRole role;
    private final ModelCapability capability;
    private final String schemaName;
    private final Map<String, String> requiredFields;
    private final String artifactName;
    private final String artifactType;

    BaseModelAgent(
        ModelGateway modelGateway,
        Validator validator,
        AgentRole role,
        ModelCapability capability,
        String schemaName,
        Map<String, String> requiredFields,
        String artifactName,
        String artifactType
    ) {
        this.modelGateway = modelGateway;
        this.validator = validator;
        this.role = role;
        this.capability = capability;
        this.schemaName = schemaName;
        this.requiredFields = requiredFields;
        this.artifactName = artifactName;
        this.artifactType = artifactType;
    }

    @Override
    public AgentRole role() {
        return role;
    }

    @Override
    public AgentExecutionResult<T> execute(AgentTask task, ExecutionContext context) {
        if (task.agent() != role) {
            throw new AgentContractException("Task " + task.id() + " is assigned to " + task.agent() + " but executor is " + role);
        }
        ModelResult modelResult = modelGateway.invoke(new ModelRequest(
            capability,
            role.name(),
            schemaName,
            prompt(task, context),
            requiredFields,
            new LinkedHashMap<>(task.inputs())
        ));
        T output = map(task, context, modelResult.fields());
        validate(output);
        AgentArtifact artifact = AgentArtifact.of(artifactName, artifactType, renderArtifact(output), task);
        validate(artifact);
        return new AgentExecutionResult<>(task.id(), role, output, List.of(artifact), modelResult);
    }

    protected abstract T map(AgentTask task, ExecutionContext context, Map<String, String> fields);

    protected String prompt(AgentTask task, ExecutionContext context) {
        return """
            Workflow: %s
            Revision: %d
            Requirement: %s
            Task: %s
            Goal: %s
            Known artifacts: %s
            Inputs: %s
            """.formatted(
            context.workflowId(),
            context.revision(),
            context.requirement(),
            task.id(),
            task.goal(),
            context.artifacts().keySet(),
            task.inputs()
        );
    }

    protected String renderArtifact(T output) {
        return output.toString();
    }

    private <V> void validate(V value) {
        Set<ConstraintViolation<V>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            throw new AgentContractException("Agent output violates contract: " + violations.iterator().next().getPropertyPath());
        }
    }

    protected List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return value.lines()
            .flatMap(line -> List.of(line.split("\\s*;\\s*|\\s*,\\s*")).stream())
            .map(item -> item.replaceFirst("^[-*]\\s*", "").trim())
            .filter(item -> !item.isBlank())
            .toList();
    }
}

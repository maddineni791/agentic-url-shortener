package com.assessment.agentic.agents;

public interface AgentExecutor<T> {

    AgentRole role();

    AgentExecutionResult<T> execute(AgentTask task, ExecutionContext context);
}

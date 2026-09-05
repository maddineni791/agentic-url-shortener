package com.assessment.agentic.tools;

public interface EngineeringTool {

    String name();

    ToolResult execute(ToolRequest request);
}

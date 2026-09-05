package com.assessment.agentic.repository;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentic.workspace")
public class RepositoryWorkspaceProperties {

    private Path root = Path.of("target", "agent-workspaces");
    private int maxFileBytes = 200_000;
    private int maxOperations = 100;

    public Path getRoot() {
        return root;
    }

    public void setRoot(Path root) {
        this.root = root;
    }

    public int getMaxFileBytes() {
        return maxFileBytes;
    }

    public void setMaxFileBytes(int maxFileBytes) {
        this.maxFileBytes = maxFileBytes;
    }

    public int getMaxOperations() {
        return maxOperations;
    }

    public void setMaxOperations(int maxOperations) {
        this.maxOperations = maxOperations;
    }
}

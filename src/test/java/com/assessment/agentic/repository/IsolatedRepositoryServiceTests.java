package com.assessment.agentic.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.assessment.agentic.agents.FileOperationProposal;
import com.assessment.agentic.agents.FileOperationProposalSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IsolatedRepositoryServiceTests {

    @TempDir
    private Path tempDir;

    @Test
    void appliesAllowedOperationsOnlyInsideIsolatedWorkspace() throws Exception {
        IsolatedRepositoryService service = service();
        FileOperationProposal production = create("src/main/java/com/example/App.java", "class App {}\n");
        FileOperationProposal test = create("src/test/java/com/example/AppTests.java", "class AppTests {}\n");

        PatchApplicationResult result = service.apply("wf-1", 1, List.of(new FileOperationProposalSet(
            List.of(production, test),
            List.of(production.normalizedRelativePath(), test.normalizedRelativePath()),
            List.of("lineage")
        )));

        assertThat(result.policyDecision().allowed()).isTrue();
        assertThat(result.changedFiles()).containsExactly(production.normalizedRelativePath(), test.normalizedRelativePath());
        assertThat(result.unifiedDiff()).contains("+++ b/src/main/java/com/example/App.java");
        Path workspace = Path.of(result.workspacePath());
        assertThat(workspace).startsWith(tempDir.toAbsolutePath().normalize());
        assertThat(Files.readString(workspace.resolve("src/main/java/com/example/App.java"))).contains("class App");
        assertThat(result.baselineManifestHash()).hasSize(64);
        assertThat(result.appliedManifestHash()).hasSize(64);
        assertThat(service.manifestJson(result.workspacePath())).contains("src/main/java/com/example/App.java");
    }

    @Test
    void rejectsTraversalDuplicateAndUnsupportedExtensionBeforeWriting() {
        IsolatedRepositoryService service = service();
        FileOperationProposal traversal = create("../escape.java", "class Escape {}\n");
        FileOperationProposal script = create("scripts/run.ps1", "Remove-Item something\n");
        FileOperationProposal duplicate = create("src/main/java/com/example/App.java", "class One {}\n");
        FileOperationProposal duplicateAgain = create("src/main/java/com/example/App.java", "class Two {}\n");

        PatchApplicationResult result = service.apply("wf-2", 1, List.of(new FileOperationProposalSet(
            List.of(traversal, script, duplicate, duplicateAgain),
            List.of(),
            List.of()
        )));

        assertThat(result.policyDecision().allowed()).isFalse();
        assertThat(result.policyDecision().rejectedPaths()).contains("../escape.java", "scripts/run.ps1", "src/main/java/com/example/App.java");
        assertThat(result.workspacePath()).isBlank();
        assertThat(Files.exists(tempDir.resolve("wf-2"))).isFalse();
    }

    private IsolatedRepositoryService service() {
        RepositoryWorkspaceProperties properties = new RepositoryWorkspaceProperties();
        properties.setRoot(tempDir);
        PatchPolicyEngine policyEngine = new PatchPolicyEngine(properties);
        return new IsolatedRepositoryService(properties, policyEngine);
    }

    private FileOperationProposal create(String path, String content) {
        return new FileOperationProposal("CREATE", path, content, null, "test", "REQ-TEST", "task-test", "lineage");
    }
}

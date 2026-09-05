package com.assessment.agentic.repository;

import com.assessment.agentic.agents.FileOperationProposal;
import com.assessment.agentic.agents.FileOperationProposalSet;
import com.assessment.agentic.persistence.Hashing;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class IsolatedRepositoryService {

    private final RepositoryWorkspaceProperties properties;
    private final PatchPolicyEngine policyEngine;

    public IsolatedRepositoryService(RepositoryWorkspaceProperties properties, PatchPolicyEngine policyEngine) {
        this.properties = properties;
        this.policyEngine = policyEngine;
    }

    public PatchApplicationResult apply(String workflowId, int revision, List<FileOperationProposalSet> proposalSets) {
        List<FileOperationProposal> operations = proposalSets.stream()
            .flatMap(set -> set.fileOperations().stream())
            .toList();
        PatchPolicyDecision decision = policyEngine.evaluate(operations);
        if (!decision.allowed()) {
            return new PatchApplicationResult("", "", "", List.of(), "", decision);
        }
        try {
            Path workspace = createWorkspace(workflowId, revision);
            String baselineManifest = manifestJson(workspace);
            String baselineManifestHash = Hashing.sha256(baselineManifest);
            snapshotBaseline(workspace);
            List<String> changedFiles = new ArrayList<>();
            StringBuilder diff = new StringBuilder();
            for (FileOperationProposal operation : operations) {
                applyOperation(workspace, operation, diff);
                changedFiles.add(operation.normalizedRelativePath());
            }
            String appliedManifest = manifestJson(workspace);
            return new PatchApplicationResult(
                workspace.toAbsolutePath().normalize().toString(),
                baselineManifestHash,
                Hashing.sha256(appliedManifest),
                changedFiles,
                diff.toString(),
                decision
            );
        } catch (IOException exception) {
            throw new RepositoryMutationException("Unable to apply patch in isolated workspace.", exception);
        }
    }

    public PatchApplicationResult applyToExisting(String workspacePath, List<FileOperationProposalSet> proposalSets) {
        List<FileOperationProposal> operations = proposalSets.stream()
            .flatMap(set -> set.fileOperations().stream())
            .toList();
        PatchPolicyDecision decision = policyEngine.evaluate(operations);
        if (!decision.allowed()) {
            return new PatchApplicationResult(workspacePath, "", "", List.of(), "", decision);
        }
        try {
            Path workspace = Path.of(workspacePath).toAbsolutePath().normalize();
            String baselineManifest = manifestJson(workspace);
            List<String> changedFiles = new ArrayList<>();
            StringBuilder diff = new StringBuilder();
            for (FileOperationProposal operation : operations) {
                applyOperation(workspace, operation, diff);
                changedFiles.add(operation.normalizedRelativePath());
            }
            String appliedManifest = manifestJson(workspace);
            return new PatchApplicationResult(workspace.toString(), Hashing.sha256(baselineManifest), Hashing.sha256(appliedManifest), changedFiles, diff.toString(), decision);
        } catch (IOException exception) {
            throw new RepositoryMutationException("Unable to apply repair patch in isolated workspace.", exception);
        }
    }

    public String manifestJson(String workspacePath) {
        try {
            return manifestJson(Path.of(workspacePath));
        } catch (IOException exception) {
            throw new RepositoryMutationException("Unable to read workspace manifest.", exception);
        }
    }

    /** Resolves the workspace for a workflow revision and restores it to its immutable baseline. */
    public RollbackResult rollback(String workflowId, int revision) {
        Path root = properties.getRoot().toAbsolutePath().normalize();
        return rollback(root.resolve(workflowId).resolve("revision-" + revision).toString());
    }

    /**
     * Restores {@code workspacePath} to the immutable baseline snapshot captured when the patch was applied
     * and verifies the restored tree against the baseline SHA-256 manifest.
     */
    public RollbackResult rollback(String workspacePath) {
        if (workspacePath == null || workspacePath.isBlank()) {
            return new RollbackResult(false, false, 0, "", "no-workspace-path");
        }
        Path workspace = Path.of(workspacePath).toAbsolutePath().normalize();
        Path baseline = workspace.resolveSibling(workspace.getFileName() + "-baseline");
        try {
            if (!Files.exists(workspace) && !Files.exists(baseline)) {
                return new RollbackResult(false, false, 0, "", "workspace-missing");
            }
            if (!Files.exists(baseline)) {
                return new RollbackResult(true, false, 0, "", "baseline-missing");
            }
            if (Files.exists(workspace)) {
                deleteTree(workspace);
            }
            copyTree(baseline, workspace);
            String restoredManifest = manifestJson(workspace);
            String restoredHash = Hashing.sha256(restoredManifest);
            String baselineHash = Hashing.sha256(manifestJson(baseline));
            int restoredFiles;
            try (var stream = Files.walk(workspace)) {
                restoredFiles = (int) stream.filter(Files::isRegularFile).count();
            }
            boolean verified = restoredHash.equals(baselineHash);
            return new RollbackResult(true, verified, restoredFiles, restoredHash, verified ? "verified" : "manifest-mismatch");
        } catch (IOException exception) {
            throw new RepositoryMutationException("Unable to roll back isolated workspace.", exception);
        }
    }

    private void snapshotBaseline(Path workspace) throws IOException {
        Path baseline = workspace.resolveSibling(workspace.getFileName() + "-baseline");
        if (Files.exists(baseline)) {
            deleteTree(baseline);
        }
        copyTree(workspace, baseline);
    }

    private void copyTree(Path source, Path target) throws IOException {
        try (var stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path destination = target.resolve(source.relativize(path).toString()).normalize();
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private Path createWorkspace(String workflowId, int revision) throws IOException {
        Path root = properties.getRoot().toAbsolutePath().normalize();
        Path workspace = root.resolve(workflowId).resolve("revision-" + revision).normalize();
        if (!workspace.startsWith(root)) {
            throw new RepositoryMutationException("Workspace escaped configured root.");
        }
        if (Files.exists(workspace)) {
            deleteTree(workspace);
        }
        Files.createDirectories(workspace);
        seedBuildFiles(workspace);
        return workspace;
    }

    private void seedBuildFiles(Path workspace) throws IOException {
        Path sourceRoot = Path.of("").toAbsolutePath().normalize();
        copyIfExists(sourceRoot.resolve("pom.xml"), workspace.resolve("pom.xml"));
        copyIfExists(sourceRoot.resolve("mvnw.cmd"), workspace.resolve("mvnw.cmd"));
        copyIfExists(sourceRoot.resolve("mvnw"), workspace.resolve("mvnw"));
        Path sourceWrapper = sourceRoot.resolve(".mvn").resolve("wrapper");
        if (Files.exists(sourceWrapper)) {
            try (var stream = Files.walk(sourceWrapper)) {
                for (Path source : stream.filter(Files::isRegularFile).toList()) {
                    Path target = workspace.resolve(".mvn").resolve("wrapper").resolve(sourceWrapper.relativize(source).toString()).normalize();
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        Files.createDirectories(workspace.resolve(".mvn"));
        Path validationRepository = sourceRoot.resolve("target").resolve("validation-maven-repository").toAbsolutePath().normalize();
        Files.writeString(workspace.resolve(".mvn").resolve("maven.config"), "-Dmaven.repo.local=" + validationRepository.toString().replace('\\', '/') + "\n",
            StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        if (!Files.exists(workspace.resolve("README.md"))) {
            Files.writeString(workspace.resolve("README.md"), "# Isolated generated workspace\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        }
    }

    private void copyIfExists(Path source, Path target) throws IOException {
        if (Files.exists(source)) {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void applyOperation(Path workspace, FileOperationProposal operation, StringBuilder diff) throws IOException {
        Path target = workspace.resolve(operation.normalizedRelativePath()).normalize();
        if (!target.startsWith(workspace)) {
            throw new RepositoryMutationException("Patch target escaped isolated workspace.");
        }
        Files.createDirectories(target.getParent());
        String oldContent = Files.exists(target) ? Files.readString(target, StandardCharsets.UTF_8) : "";
        if (operation.expectedCurrentSha256() != null && !operation.expectedCurrentSha256().isBlank()
            && !Hashing.sha256(oldContent).equals(operation.expectedCurrentSha256())) {
            throw new RepositoryMutationException("Expected hash mismatch for " + operation.normalizedRelativePath());
        }
        switch (operation.operationType()) {
            case "CREATE", "UPDATE" -> Files.writeString(target, operation.completeProposedContent(), StandardCharsets.UTF_8);
            case "DELETE" -> Files.deleteIfExists(target);
            default -> throw new RepositoryMutationException("Unsupported operation type: " + operation.operationType());
        }
        appendCreateOrUpdateDiff(operation, oldContent, diff);
    }

    private void appendCreateOrUpdateDiff(FileOperationProposal operation, String oldContent, StringBuilder diff) {
        diff.append("--- a/").append(operation.normalizedRelativePath()).append('\n');
        diff.append("+++ b/").append(operation.normalizedRelativePath()).append('\n');
        for (String line : oldContent.lines().toList()) {
            diff.append('-').append(line).append('\n');
        }
        for (String line : operation.completeProposedContent().lines().toList()) {
            diff.append('+').append(line).append('\n');
        }
    }

    private String manifestJson(Path workspace) throws IOException {
        List<SourceManifestEntry> entries;
        try (var stream = Files.walk(workspace)) {
            entries = stream
                .filter(Files::isRegularFile)
                .sorted()
                .map(path -> toManifestEntry(workspace, path))
                .toList();
        }
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < entries.size(); i++) {
            SourceManifestEntry entry = entries.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"path\":\"").append(entry.path()).append("\",\"sha256\":\"")
                .append(entry.sha256()).append("\",\"bytes\":").append(entry.bytes()).append('}');
        }
        return json.append(']').toString();
    }

    private SourceManifestEntry toManifestEntry(Path workspace, Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            String relative = workspace.relativize(path).toString().replace('\\', '/');
            return new SourceManifestEntry(relative, Hashing.sha256(new String(bytes, StandardCharsets.UTF_8)), bytes.length);
        } catch (IOException exception) {
            throw new RepositoryMutationException("Unable to hash workspace file.", exception);
        }
    }

    private void deleteTree(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                Files.delete(path);
            }
        }
    }
}

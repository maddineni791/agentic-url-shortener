package com.assessment.agentic.repository;

import com.assessment.agentic.agents.FileOperationProposal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PatchPolicyEngine {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        ".java", ".xml", ".yml", ".yaml", ".properties", ".md", ".json", ".sql", ".txt"
    );

    private final RepositoryWorkspaceProperties properties;

    public PatchPolicyEngine(RepositoryWorkspaceProperties properties) {
        this.properties = properties;
    }

    public PatchPolicyDecision evaluate(List<FileOperationProposal> operations) {
        List<String> accepted = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (operations.size() > properties.getMaxOperations()) {
            reasons.add("operation count exceeds configured maximum");
        }
        for (FileOperationProposal operation : operations) {
            String path = operation.normalizedRelativePath();
            List<String> pathRejections = validatePath(path);
            if (!seen.add(path)) {
                pathRejections.add("duplicate operation for path");
            }
            if (operation.completeProposedContent().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > properties.getMaxFileBytes()) {
                pathRejections.add("proposed content exceeds byte limit");
            }
            if (operation.operationType().equals("UPDATE") || operation.operationType().equals("DELETE")) {
                if (operation.expectedCurrentSha256() == null || operation.expectedCurrentSha256().isBlank()) {
                    pathRejections.add("update/delete operation requires expected current SHA-256");
                }
            }
            if (pathRejections.isEmpty()) {
                accepted.add(path);
            } else {
                rejected.add(path);
                reasons.add(path + ": " + String.join("; ", pathRejections));
            }
        }
        return new PatchPolicyDecision(reasons.isEmpty(), accepted, rejected, reasons);
    }

    private List<String> validatePath(String value) {
        List<String> reasons = new ArrayList<>();
        Path path = Path.of(value).normalize();
        if (path.isAbsolute()) {
            reasons.add("absolute paths are rejected");
        }
        if (!path.toString().replace('\\', '/').equals(value)) {
            reasons.add("path must already be normalized with forward slashes");
        }
        if (value.contains("..")) {
            reasons.add("path traversal is rejected");
        }
        if (value.isBlank()) {
            reasons.add("path is blank");
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (ALLOWED_EXTENSIONS.stream().noneMatch(lower::endsWith)) {
            reasons.add("file extension is not allowlisted");
        }
        return reasons;
    }
}

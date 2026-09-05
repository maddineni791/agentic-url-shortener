package com.assessment.agentic.repository;

/**
 * Outcome of restoring an isolated workspace to its immutable baseline snapshot.
 *
 * @param workspaceExisted     whether a workspace directory was present to roll back
 * @param verified             whether the restored tree's SHA-256 manifest matches the baseline manifest
 * @param restoredFiles        number of regular files present after restoration
 * @param restoredManifestHash SHA-256 of the restored workspace manifest
 * @param detail               short machine-readable explanation (e.g. {@code verified}, {@code baseline-missing})
 */
public record RollbackResult(
    boolean workspaceExisted,
    boolean verified,
    int restoredFiles,
    String restoredManifestHash,
    String detail
) {
}

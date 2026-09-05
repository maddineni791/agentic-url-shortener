package com.assessment.agentic.repository;

public record SourceManifestEntry(
    String path,
    String sha256,
    long bytes
) {
}

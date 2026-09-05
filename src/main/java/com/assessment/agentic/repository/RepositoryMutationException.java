package com.assessment.agentic.repository;

public class RepositoryMutationException extends RuntimeException {

    public RepositoryMutationException(String message) {
        super(message);
    }

    public RepositoryMutationException(String message, Throwable cause) {
        super(message, cause);
    }
}

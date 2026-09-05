package com.assessment.agentic.model;

public class ModelInvocationException extends RuntimeException {

    public ModelInvocationException(String message) {
        super(message);
    }

    public ModelInvocationException(String message, Throwable cause) {
        super(message, cause);
    }
}

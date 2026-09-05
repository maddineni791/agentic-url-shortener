package com.assessment.agentic.urlshortener;

public class UrlShortenerException extends RuntimeException {

    private final String code;

    public UrlShortenerException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

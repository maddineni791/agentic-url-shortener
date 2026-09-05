package com.assessment.agentic.api;

import com.assessment.agentic.urlshortener.UrlShortenerException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validationProblem(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed.");
        problem.setType(URI.create("https://agentic-url-shortener.local/problems/validation-failed"));
        problem.setTitle("Validation failed");
        problem.setProperty("code", "VALIDATION_FAILED");
        problem.setProperty("correlationId", correlationId(request));
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(this::fieldMessage)
            .toList();
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail responseStatusProblem(ResponseStatusException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
        problem.setType(URI.create("https://agentic-url-shortener.local/problems/request-rejected"));
        problem.setTitle("Request rejected");
        problem.setProperty("code", "REQUEST_REJECTED");
        problem.setProperty("correlationId", correlationId(request));
        return problem;
    }

    @ExceptionHandler(UrlShortenerException.class)
    ProblemDetail urlShortenerProblem(UrlShortenerException ex, HttpServletRequest request) {
        HttpStatus status = switch (ex.code()) {
            case "SHORT_CODE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "SHORT_URL_EXPIRED", "SHORT_URL_DEACTIVATED" -> HttpStatus.GONE;
            default -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setType(URI.create("https://agentic-url-shortener.local/problems/url-shortener"));
        problem.setTitle("URL shortener request rejected");
        problem.setProperty("code", ex.code());
        problem.setProperty("correlationId", correlationId(request));
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "The authenticated role cannot perform this action.");
        problem.setType(URI.create("https://agentic-url-shortener.local/problems/forbidden"));
        problem.setTitle("Forbidden");
        problem.setProperty("code", "FORBIDDEN");
        problem.setProperty("correlationId", correlationId(request));
        return problem;
    }

    private String fieldMessage(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.HEADER);
        String header = request.getHeader(CorrelationIdFilter.HEADER);
        return header == null || header.isBlank() ? String.valueOf(value) : header;
    }
}

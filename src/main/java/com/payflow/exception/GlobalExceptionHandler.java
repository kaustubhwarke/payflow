package com.payflow.exception;

import com.payflow.filter.TracingFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Centralised translation of exceptions into RFC 7807 {@link ProblemDetail} responses
 * (Rule 10: graceful handling, never an abrupt crash; mentor feedback 22.b: strong contract).
 *
 * <p>Every response carries the {@code errorCode}, the originating {@code traceId} (so a client
 * report can be correlated with server logs), and a timestamp.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final URI PROBLEM_BASE = URI.create("https://docs.payflow.com/errors/");

    /** Handles all domain exceptions via their {@link ErrorCode}. */
    @ExceptionHandler(PayflowException.class)
    public ResponseEntity<ProblemDetail> handlePayflow(PayflowException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        if (code.getStatus().is5xxServerError()) {
            log.error("Domain error [{}] on {}: {}", code, request.getRequestURI(), ex.getMessage(), ex);
        } else {
            log.warn("Domain error [{}] on {}: {}", code, request.getRequestURI(), ex.getMessage());
        }
        return build(code, ex.getMessage(), request);
    }

    /** Handles {@code @Valid} body validation failures, aggregating per-field messages. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleBodyValidation(MethodArgumentNotValidException ex,
                                                              HttpServletRequest request) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.toList());
        ProblemDetail problem = baseProblem(ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.getDefaultMessage(), request);
        problem.setProperty("errors", errors);
        log.warn("Validation failed on {}: {}", request.getRequestURI(), errors);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus()).body(problem);
    }

    /** Handles constraint violations on path/query parameters. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex,
                                                                   HttpServletRequest request) {
        return build(ErrorCode.VALIDATION_FAILED, ex.getMessage(), request);
    }

    /**
     * Translates Hibernate optimistic-lock failures into a retryable 409. This is the
     * database-level safety net that complements application-level striped locking
     * (mentor feedback 22.a).
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                                              HttpServletRequest request) {
        log.warn("Optimistic lock conflict on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.CONCURRENCY_CONFLICT, ErrorCode.CONCURRENCY_CONFLICT.getDefaultMessage(), request);
    }

    /** Maps Spring Security authorization denials to 403. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getDefaultMessage(), request);
    }

    /** Maps authentication failures to 401. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return build(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getDefaultMessage(), request);
    }

    /** Final safety net: nothing escapes as an unstructured 500 / stack trace. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage(), request);
    }

    private ResponseEntity<ProblemDetail> build(ErrorCode code, String detail, HttpServletRequest request) {
        return ResponseEntity.status(code.getStatus()).body(baseProblem(code, detail, request));
    }

    private ProblemDetail baseProblem(ErrorCode code, String detail, HttpServletRequest request) {
        HttpStatus status = code.getStatus();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(PROBLEM_BASE.resolve(code.name().toLowerCase()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", code.name());
        problem.setProperty("traceId", MDC.get(TracingFilter.TRACE_ID_KEY));
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}

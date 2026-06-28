package com.payflow.exception;

import com.payflow.filter.TracingFilter;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GlobalExceptionHandler} (AAA pattern).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users");

    @BeforeEach
    void setTrace() {
        MDC.put(TracingFilter.TRACE_ID_KEY, "0123456789abcdef0123456789abcdef");
    }

    @AfterEach
    void clearTrace() {
        MDC.clear();
    }

    @SuppressWarnings("unused")
    private void dummy(String value) {
        // target for MethodParameter construction
    }

    @Test
    void handlePayflow_mapsClientError() {
        // Act
        ResponseEntity<ProblemDetail> result =
                handler.handlePayflow(ResourceNotFoundException.user("priya@okaxis"), request);

        // Assert
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getProperties()).containsEntry("errorCode", "USER_NOT_FOUND");
        assertThat(result.getBody().getProperties())
                .containsEntry("traceId", "0123456789abcdef0123456789abcdef");
    }

    @Test
    void handlePayflow_mapsServerErrorBranch() {
        // Arrange: a 5xx-mapped domain exception exercises the error-log branch
        PayflowException serverError = new PayflowException(ErrorCode.INTERNAL_ERROR, "boom") { };

        // Act
        ResponseEntity<ProblemDetail> result = handler.handlePayflow(serverError, request);

        // Assert
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void handleBodyValidation_aggregatesFieldErrors() throws NoSuchMethodException {
        // Arrange
        Method method = getClass().getDeclaredMethod("dummy", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", "name is required"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        // Act
        ResponseEntity<ProblemDetail> result = handler.handleBodyValidation(ex, request);

        // Assert
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getProperties()).containsKey("errors");
        @SuppressWarnings("unchecked")
        java.util.List<String> errors =
                (java.util.List<String>) result.getBody().getProperties().get("errors");
        assertThat(errors).contains("name: name is required");
    }

    @Test
    void handleConstraintViolation_mapsToBadRequest() {
        // Arrange
        ConstraintViolationException ex = new ConstraintViolationException("invalid", Collections.emptySet());

        // Act
        ResponseEntity<ProblemDetail> result = handler.handleConstraintViolation(ex, request);

        // Assert
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleOptimisticLock_mapsToConflict() {
        // Act
        ResponseEntity<ProblemDetail> result =
                handler.handleOptimisticLock(new ObjectOptimisticLockingFailureException(Object.class, 1L), request);

        // Assert
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(result.getBody().getProperties()).containsEntry("errorCode", "CONCURRENCY_CONFLICT");
    }

    @Test
    void handleAccessDenied_mapsToForbidden() {
        ResponseEntity<ProblemDetail> result =
                handler.handleAccessDenied(new AccessDeniedException("denied"), request);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void handleAuthentication_mapsToUnauthorized() {
        ResponseEntity<ProblemDetail> result =
                handler.handleAuthentication(new BadCredentialsException("bad"), request);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void handleUnexpected_mapsToInternalServerError() {
        ResponseEntity<ProblemDetail> result =
                handler.handleUnexpected(new RuntimeException("kaboom"), request);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.getBody().getProperties()).containsEntry("errorCode", "INTERNAL_ERROR");
    }
}

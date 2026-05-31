package com.payflow.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error catalogue. Each constant binds a business error to the HTTP
 * status the API returns, giving clients a contract they can branch on without parsing prose
 * (mentor feedback 22.b).
 */
public enum ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested user does not exist"),
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested transaction does not exist"),
    DUPLICATE_UPI_ID(HttpStatus.CONFLICT, "A user with this UPI ID already exists"),
    INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_ENTITY, "The sender has insufficient balance"),
    INVALID_TRANSFER(HttpStatus.BAD_REQUEST, "The transfer request is invalid"),
    CONCURRENCY_CONFLICT(HttpStatus.CONFLICT, "The resource was modified concurrently; retry the operation"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "One or more fields failed validation"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests; slow down"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required or the token is invalid"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}

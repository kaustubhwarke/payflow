package com.payflow.exception;

import lombok.Getter;

/**
 * Base type for all domain exceptions (OOP: a single inheritance root so the global handler
 * can treat business failures uniformly). Carries an {@link ErrorCode} that maps to both an
 * HTTP status and a stable machine-readable code.
 */
@Getter
public abstract class PayflowException extends RuntimeException {

    private final transient ErrorCode errorCode;

    protected PayflowException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}

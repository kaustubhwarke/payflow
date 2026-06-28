package com.payflow.exception;

/**
 * Thrown when a caller exceeds the configured request quota. Maps to HTTP 429.
 */
public class RateLimitExceededException extends PayflowException {

    public RateLimitExceededException(String message) {
        super(ErrorCode.RATE_LIMIT_EXCEEDED, message);
    }
}

package com.payflow.exception;

/**
 * Thrown when a requested aggregate cannot be located. Maps to HTTP 404.
 */
public class ResourceNotFoundException extends PayflowException {

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    /** @return a not-found exception for a user identified by {@code identifier}. */
    public static ResourceNotFoundException user(String identifier) {
        return new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND,
                "No user found for identifier '" + identifier + "'");
    }

    /** @return a not-found exception for a transaction identified by {@code reference}. */
    public static ResourceNotFoundException transaction(String reference) {
        return new ResourceNotFoundException(ErrorCode.TRANSACTION_NOT_FOUND,
                "No transaction found for reference '" + reference + "'");
    }
}

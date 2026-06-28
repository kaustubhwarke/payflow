package com.payflow.exception;

/**
 * Thrown when creating a resource would violate a uniqueness invariant (e.g. duplicate UPI
 * ID). Maps to HTTP 409.
 */
public class DuplicateResourceException extends PayflowException {

    public DuplicateResourceException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    /** @return a conflict exception for an already-registered {@code upiId}. */
    public static DuplicateResourceException upiId(String upiId) {
        return new DuplicateResourceException(ErrorCode.DUPLICATE_UPI_ID,
                "UPI ID '" + upiId + "' is already registered");
    }
}

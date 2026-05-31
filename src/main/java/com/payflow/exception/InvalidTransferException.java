package com.payflow.exception;

/**
 * Thrown when a transfer request is semantically invalid (e.g. sender equals receiver).
 * Maps to HTTP 400.
 */
public class InvalidTransferException extends PayflowException {

    public InvalidTransferException(String message) {
        super(ErrorCode.INVALID_TRANSFER, message);
    }
}

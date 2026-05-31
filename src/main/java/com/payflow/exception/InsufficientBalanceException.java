package com.payflow.exception;

/**
 * Thrown when a sender's wallet cannot cover a requested transfer. Maps to HTTP 422.
 */
public class InsufficientBalanceException extends PayflowException {

    public InsufficientBalanceException(String message) {
        super(ErrorCode.INSUFFICIENT_BALANCE, message);
    }
}

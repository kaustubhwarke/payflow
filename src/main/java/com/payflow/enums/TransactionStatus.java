package com.payflow.enums;

/**
 * Lifecycle states of a money transfer.
 *
 * <p>The happy path is {@code PENDING -> COMPLETED}. A transfer that fails validation or
 * balance checks is persisted as {@code FAILED} so it remains auditable, and an already
 * completed transfer can be {@code REVERSED} by a compensating operation.</p>
 */
public enum TransactionStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REVERSED
}

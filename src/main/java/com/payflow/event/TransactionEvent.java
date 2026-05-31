package com.payflow.event;

import com.payflow.enums.TransactionStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable domain event published when a transaction reaches a terminal state. Consumed
 * asynchronously (e.g. by the notification handler) so the write path never blocks on
 * downstream side-effects (Rule 15).
 *
 * @param transactionReference opaque transaction reference
 * @param senderUpiId          paying party
 * @param receiverUpiId        receiving party
 * @param amount               transferred amount
 * @param status               terminal status (COMPLETED / FAILED)
 * @param occurredAt           event timestamp
 * @param traceId              originating trace id for cross-service correlation
 */
public record TransactionEvent(
        String transactionReference,
        String senderUpiId,
        String receiverUpiId,
        BigDecimal amount,
        TransactionStatus status,
        Instant occurredAt,
        String traceId
) implements Serializable {
}

package com.payflow.messaging;

import com.payflow.enums.TransactionStatus;
import com.payflow.event.TransactionEvent;
import com.payflow.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link TransactionEventConsumer} (AAA pattern).
 */
@ExtendWith(MockitoExtension.class)
class TransactionEventConsumerTest {

    @Mock
    private AuditService auditService;
    @InjectMocks
    private TransactionEventConsumer consumer;

    @Test
    void onTransactionEvent_recordsAuditEntry() {
        // Arrange
        TransactionEvent event = new TransactionEvent("TXN_1", "alice@okaxis", "bob@oksbi",
                new BigDecimal("250.00"), TransactionStatus.COMPLETED, Instant.now(), "trace-123");

        // Act
        consumer.onTransactionEvent(event);

        // Assert
        verify(auditService).record(eq("TRANSACTION_NOTIFIED"), eq("notification-service"),
                eq("Transaction"), eq("TXN_1"), contains("COMPLETED"));
    }
}

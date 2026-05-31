package com.payflow.messaging;

import com.payflow.config.RabbitConfig;
import com.payflow.event.TransactionEvent;
import com.payflow.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link TransactionEvent}s from the notifications queue (Rule 15). In a real system
 * this would dispatch push/SMS notifications; here it records an audit entry and emits a
 * structured log line, propagating the originating {@code traceId} so the asynchronous work is
 * correlated with the request that triggered it.
 */
@Component
public class TransactionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventConsumer.class);

    private final AuditService auditService;

    public TransactionEventConsumer(AuditService auditService) {
        this.auditService = auditService;
    }

    @RabbitListener(queues = RabbitConfig.TRANSACTION_QUEUE)
    public void onTransactionEvent(TransactionEvent event) {
        MDC.put("traceId", event.traceId());
        try {
            log.info("Notification handler received transaction event reference={} status={}",
                    event.transactionReference(), event.status());
            auditService.record(
                    "TRANSACTION_NOTIFIED",
                    "notification-service",
                    "Transaction",
                    event.transactionReference(),
                    "Notified parties of status " + event.status());
        } finally {
            MDC.remove("traceId");
        }
    }
}

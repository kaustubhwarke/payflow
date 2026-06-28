package com.payflow.messaging;

import com.payflow.config.RabbitConfig;
import com.payflow.enums.TransactionStatus;
import com.payflow.event.TransactionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ-backed {@link TransactionEventPublisher}. Publication runs on the async executor
 * (Rule 15) so the caller's transaction commit is never delayed by broker I/O. Routing key is
 * derived from the event's terminal status.
 */
@Component
public class RabbitTransactionEventPublisher implements TransactionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitTransactionEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitTransactionEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    @Async
    public void publish(TransactionEvent event) {
        String routingKey = event.status() == TransactionStatus.COMPLETED
                ? RabbitConfig.ROUTING_KEY_COMPLETED
                : RabbitConfig.ROUTING_KEY_FAILED;
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, routingKey, event);
            log.info("Published transaction event reference={} status={} routingKey={}",
                    event.transactionReference(), event.status(), routingKey);
        } catch (RuntimeException ex) {
            // Never let a messaging failure break the business flow; the transaction is already
            // durably persisted. Log for reconciliation/retry.
            log.error("Failed to publish transaction event reference={}",
                    event.transactionReference(), ex);
        }
    }
}

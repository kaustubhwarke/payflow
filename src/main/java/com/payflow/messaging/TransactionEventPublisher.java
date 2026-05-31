package com.payflow.messaging;

import com.payflow.event.TransactionEvent;

/**
 * Abstraction for publishing {@link TransactionEvent}s (DIP: services depend on this interface,
 * not on RabbitMQ directly, so the broker can be swapped without touching business logic).
 */
public interface TransactionEventPublisher {

    /**
     * Publishes a transaction event asynchronously.
     *
     * @param event the event to publish
     */
    void publish(TransactionEvent event);
}

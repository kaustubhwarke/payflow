package com.payflow.messaging;

import com.payflow.config.RabbitConfig;
import com.payflow.enums.TransactionStatus;
import com.payflow.event.TransactionEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link RabbitTransactionEventPublisher} (AAA pattern).
 */
@ExtendWith(MockitoExtension.class)
class RabbitTransactionEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;
    @InjectMocks
    private RabbitTransactionEventPublisher publisher;

    private TransactionEvent event(TransactionStatus status) {
        return new TransactionEvent("TXN_1", "alice@okaxis", "bob@oksbi",
                new BigDecimal("250.00"), status, Instant.now(), "trace");
    }

    @Test
    void publish_completedEvent_usesCompletedRoutingKey() {
        // Act
        publisher.publish(event(TransactionStatus.COMPLETED));

        // Assert
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.EXCHANGE),
                eq(RabbitConfig.ROUTING_KEY_COMPLETED), any(TransactionEvent.class));
    }

    @Test
    void publish_failedEvent_usesFailedRoutingKey() {
        // Act
        publisher.publish(event(TransactionStatus.FAILED));

        // Assert
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.EXCHANGE),
                eq(RabbitConfig.ROUTING_KEY_FAILED), any(TransactionEvent.class));
    }

    @Test
    void publish_swallowsBrokerFailure() {
        // Arrange
        doThrow(new org.springframework.amqp.AmqpException("broker down"))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));

        // Act + Assert
        assertThatCode(() -> publisher.publish(event(TransactionStatus.COMPLETED)))
                .doesNotThrowAnyException();
    }
}

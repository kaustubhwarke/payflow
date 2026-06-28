package com.payflow.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for asynchronous, event-driven flows (Rule 15).
 *
 * <p>Declares a topic exchange and a durable notifications queue bound to completed/failed
 * transaction routing keys, plus a dead-letter queue so poison messages are quarantined rather
 * than lost. Messages are serialised as JSON for cross-language interoperability.</p>
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "payflow.exchange";
    public static final String TRANSACTION_QUEUE = "payflow.transaction.notifications";
    public static final String DLQ = "payflow.transaction.notifications.dlq";
    public static final String ROUTING_KEY_PATTERN = "transaction.*";
    public static final String ROUTING_KEY_COMPLETED = "transaction.completed";
    public static final String ROUTING_KEY_FAILED = "transaction.failed";

    @Bean
    public TopicExchange payflowExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Queue transactionQueue() {
        return QueueBuilder.durable(TRANSACTION_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", DLQ)
                .build();
    }

    @Bean
    public Binding transactionBinding(Queue transactionQueue, TopicExchange payflowExchange) {
        return BindingBuilder.bind(transactionQueue).to(payflowExchange).with(ROUTING_KEY_PATTERN);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setExchange(EXCHANGE);
        return template;
    }
}

package com.smartportx.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the domain-event exchange and the notification consumer queue. Mirrors
 * producers (container/yard/gate/vessel/integration) which publish to
 * {@code smartportx.domain-events} with routing key = event type. Only active when
 * Rabbit consumption is enabled, so tests and log-based runs exclude the broker.
 */
@Configuration
@ConditionalOnProperty(name = "smartportx.events.consume-from-rabbitmq", havingValue = "true")
public class RabbitEventsConfig {

    @Bean
    public TopicExchange domainEventsExchange(
            @Value("${smartportx.events.exchange:smartportx.domain-events}") String exchange) {
        return new TopicExchange(exchange);
    }

    @Bean
    public Queue notificationDomainEventsQueue(
            @Value("${smartportx.events.queue:notification.domain-events}") String queue) {
        return new Queue(queue, true);
    }

    @Bean
    public Binding notificationDomainEventsBinding(TopicExchange domainEventsExchange,
                                                   Queue notificationDomainEventsQueue) {
        return BindingBuilder.bind(notificationDomainEventsQueue).to(domainEventsExchange).with("#");
    }
}
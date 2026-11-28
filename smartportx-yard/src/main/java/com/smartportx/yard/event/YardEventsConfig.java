package com.smartportx.yard.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class YardEventsConfig {

    @Bean
    @ConditionalOnProperty(name = "smartportx.events.publish-to-rabbitmq", havingValue = "false", matchIfMissing = true)
    public EventTransport logEventTransport() {
        return new LogEventTransport();
    }

    @Bean
    @ConditionalOnProperty(name = "smartportx.events.publish-to-rabbitmq", havingValue = "true")
    public EventTransport rabbitEventTransport(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper,
                                               @Value("${smartportx.events.exchange}") String exchange) {
        return new RabbitEventTransport(rabbitTemplate, objectMapper, exchange);
    }
}
package com.smartportx.vessel.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartportx.common.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class RabbitEventTransport implements EventTransport {

    private static final Logger log = LoggerFactory.getLogger(RabbitEventTransport.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String exchange;

    public RabbitEventTransport(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper, String exchange) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.exchange = exchange;
    }

    @Override
    public void publish(DomainEvent<?> event) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(event);
            rabbitTemplate.convertAndSend(exchange, event.eventType(), body);
        } catch (Exception e) {
            log.warn("Failed to publish domain event {} (id={}): {}",
                    event.eventType(), event.eventId(), e.getMessage());
        }
    }
}
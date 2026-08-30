package com.smartportx.container.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartportx.common.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.concurrent.atomic.AtomicLong;

public final class RabbitEventTransport implements EventTransport {

    private static final Logger LOG = LoggerFactory.getLogger(RabbitEventTransport.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final ObjectMapper objectMapper;
    private final AtomicLong published = new AtomicLong();

    public RabbitEventTransport(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper, String exchange) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.exchange = exchange;
    }

    @Override
    public void publish(DomainEvent<?> event) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(event);
            rabbitTemplate.convertAndSend(exchange, event.eventType(), body, message -> {
                MessageProperties props = message.getMessageProperties();
                props.setContentType("application/json");
                props.setContentEncoding("UTF-8");
                return message;
            });
            published.incrementAndGet();
        } catch (Exception ex) {
            LOG.error("rabbit publish failed for {} ({}); event dropped", event.eventType(), ex.getMessage());
        }
    }

    public long publishedCount() {
        return published.get();
    }
}
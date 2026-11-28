package com.smartportx.notification.rabbit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartportx.common.event.DomainEvent;
import com.smartportx.notification.service.NotificationEventRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumes domain events from {@code notification.domain-events} and routes them to
 * STOMP topics. The Rabbit wiring is conditional; when consumption is disabled the
 * bean is absent and the router can still be driven directly (tests, log fallback).
 */
@Component
@ConditionalOnProperty(name = "smartportx.events.consume-from-rabbitmq", havingValue = "true")
public class RabbitInboundEventSource {

    private static final Logger LOG = LoggerFactory.getLogger(RabbitInboundEventSource.class);

    private static final TypeReference<DomainEvent<JsonNode>> EVENT_TYPE =
            new TypeReference<DomainEvent<JsonNode>>() { };

    private final ObjectMapper objectMapper;
    private final NotificationEventRouter eventRouter;

    public RabbitInboundEventSource(ObjectMapper objectMapper, NotificationEventRouter eventRouter) {
        this.objectMapper = objectMapper;
        this.eventRouter = eventRouter;
    }

    @RabbitListener(queues = "${smartportx.events.queue:notification.domain-events}")
    public void onDomainEvent(Message message) {
        try {
            DomainEvent<JsonNode> event = objectMapper.readValue(message.getBody(), EVENT_TYPE);
            LOG.debug("consumed domain event type={} id={}", event.eventType(), event.eventId());
            eventRouter.handleEvent(event);
        } catch (IOException e) {
            LOG.error("malformed domain event rejected (will not requeue): {}",
                    new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8), e);
            throw new AmqpRejectAndDontRequeueException(e);
        }
    }
}
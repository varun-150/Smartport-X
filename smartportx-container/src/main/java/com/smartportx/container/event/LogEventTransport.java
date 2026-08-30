package com.smartportx.container.event;

import com.smartportx.common.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LogEventTransport implements EventTransport {

    private static final Logger LOG = LoggerFactory.getLogger(LogEventTransport.class);

    @Override
    public void publish(DomainEvent<?> event) {
        LOG.info("DOMAIN-EVENT type={} eventId={} source={} correlationId={} payload={}",
                event.eventType(), event.eventId(), event.source(), event.correlationId(), event.payload());
    }
}
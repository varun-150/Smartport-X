package com.smartportx.integration.event;

import com.smartportx.common.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogEventTransport implements EventTransport {

    private static final Logger log = LoggerFactory.getLogger(LogEventTransport.class);

    @Override
    public void publish(DomainEvent<?> event) {
        log.info("Publishing domain event {} (id={})", event.eventType(), event.eventId());
    }
}

package com.smartportx.vessel.service;

import com.smartportx.common.event.DomainEvent;
import com.smartportx.vessel.config.VesselProperties;
import com.smartportx.vessel.event.EventTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class VesselEventLogger {

    private static final Logger log = LoggerFactory.getLogger(VesselEventLogger.class);

    private final EventTransport transport;
    private final VesselProperties properties;

    public VesselEventLogger(EventTransport transport, VesselProperties properties) {
        this.transport = transport;
        this.properties = properties;
    }

    public void publish(String type, Object payload) {
        DomainEvent<Object> event = DomainEvent.of(type, properties.getSource(), payload);
        log.info("Publishing domain event {} (id={})", type, event.eventId());
        transport.publish(event);
    }
}
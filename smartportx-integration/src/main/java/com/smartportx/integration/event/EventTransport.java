package com.smartportx.integration.event;

import com.smartportx.common.event.DomainEvent;

public interface EventTransport {

    void publish(DomainEvent<?> event);
}

package com.smartportx.container.domain.events;

import java.time.Instant;
import java.util.UUID;

public final class ContainerDomainEvents {

    public static final String REGISTERED = "container.ContainerRegistered";
    public static final String STATUS_CHANGED = "container.ContainerStatusChanged";
    public static final String MOVED = "container.ContainerMoved";
    public static final String REMOVED = "container.ContainerRemoved";

    public static final String LOG_REGISTERED = "REGISTERED";
    public static final String LOG_STATUS_CHANGE = "STATUS_CHANGE";
    public static final String LOG_MOVEMENT = "MOVEMENT";
    public static final String LOG_REMOVED = "REMOVED";

    private ContainerDomainEvents() {
    }

    public record RegisteredPayload(UUID containerId, String containerNumber, String containerType,
                                    String owner, String currentLocation, boolean dangerousGoods,
                                    Instant createdAt) {
    }

    public record StatusChangedPayload(UUID containerId, String containerNumber, String previousStatus,
                                       String newStatus, String location, Instant changedAt) {
    }

    public record MovedPayload(UUID containerId, String containerNumber, String movementType,
                               String from, String to, Instant timestamp, String performedBy) {
    }

    public record RemovedPayload(UUID containerId, String containerNumber, Instant removedAt) {
    }
}
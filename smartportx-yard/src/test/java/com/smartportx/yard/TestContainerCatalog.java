package com.smartportx.yard;

import com.smartportx.yard.service.ContainerCatalog;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class TestContainerCatalog implements ContainerCatalog {

    private static final TestContainerCatalog INSTANCE = new TestContainerCatalog();
    private static final AtomicInteger COUNTER = new AtomicInteger(1);

    private final Map<UUID, ContainerInfo> byId = new HashMap<>();

    private TestContainerCatalog() {
    }

    public static TestContainerCatalog instance() {
        return INSTANCE;
    }

    public static void reset() {
        INSTANCE.byId.clear();
    }

    public static UUID put(ContainerInfo info) {
        INSTANCE.byId.put(info.id(), info);
        return info.id();
    }

    public static UUID standard() {
        return put(standard(UUID.randomUUID()));
    }

    public static UUID heavy() {
        return put(heavy(UUID.randomUUID()));
    }

    public static UUID dangerous() {
        return put(dangerous(UUID.randomUUID()));
    }

    public static UUID reefer() {
        return put(reefer(UUID.randomUUID()));
    }

    public static UUID dispatched() {
        return put(new ContainerInfo(UUID.randomUUID(), nextNumber(), "40HC", 25000.0, false, "DISPATCHED",
                Instant.now(), null));
    }

    @Override
    public Optional<ContainerInfo> findById(UUID containerId) {
        return Optional.ofNullable(byId.get(containerId));
    }

    public static ContainerInfo standard(UUID id) {
        return new ContainerInfo(id, nextNumber(), "40HC", 25000.0, false, "IN_YARD", Instant.now(), null);
    }

    public static ContainerInfo heavy(UUID id) {
        return new ContainerInfo(id, nextNumber(), "40HC", 90000.0, false, "IN_YARD", Instant.now(), null);
    }

    public static ContainerInfo dangerous(UUID id) {
        return new ContainerInfo(id, nextNumber(), "20DC", 20000.0, true, "IN_YARD", Instant.now(), null);
    }

    public static ContainerInfo reefer(UUID id) {
        return new ContainerInfo(id, nextNumber(), "40RF", 30000.0, false, "IN_YARD", Instant.now(), null);
    }

    private static String nextNumber() {
        return String.format("YARD%07d", COUNTER.getAndIncrement());
    }
}
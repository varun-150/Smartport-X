package com.smartportx.yard;

import com.smartportx.common.event.DomainEvent;
import com.smartportx.yard.domain.YardBlock;
import com.smartportx.yard.domain.YardTier;
import com.smartportx.yard.domain.events.YardDomainEvents;
import com.smartportx.yard.event.EventTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class YardEventsIntegrationTest extends BaseYardIntegrationTest {

    @MockitoBean
    private EventTransport transport;

    private YardBlock block;

    @BeforeEach
    void setUp() {
        TestContainerCatalog.reset();
        block = block("B01");
    }

    private YardTier firstTier() {
        var bay = bayRepository.findByBlockIdOrderByBayNumberAsc(block.getId()).get(0);
        var row = rowRepository.findByBayIdOrderByRowNumberAsc(bay.getId()).get(0);
        return tierRepository.findByRowIdOrderByTierNumberAsc(row.getId()).get(0);
    }

    @Test
    void assign_publishesPositionAssigned() throws Exception {
        YardTier tier = firstTier();
        UUID containerId = TestContainerCatalog.standard();
        performPut(tierUrl(tier), objectMapper.writeValueAsString(Map.of("containerId", containerId)),
                YardTestTokens.yardManager()).andExpect(status().isOk());

        ArgumentCaptor<DomainEvent<?>> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(transport).publish(captor.capture());
        DomainEvent<?> event = captor.getValue();
        assertThat(event.eventType()).isEqualTo(YardDomainEvents.POSITION_ASSIGNED);
        assertThat(event.source()).isEqualTo("smartportx-yard");
        YardDomainEvents.PositionAssignedPayload payload =
                (YardDomainEvents.PositionAssignedPayload) event.payload();
        assertThat(payload.containerId()).isEqualTo(containerId);
        assertThat(payload.position()).contains("B01-B");
    }

    @Test
    void remove_publishesPositionFreed() throws Exception {
        YardTier tier = firstTier();
        UUID containerId = TestContainerCatalog.standard();
        performPut(tierUrl(tier), objectMapper.writeValueAsString(Map.of("containerId", containerId)),
                YardTestTokens.yardManager()).andExpect(status().isOk());
        performDelete(tierUrl(tier), YardTestTokens.yardManager()).andExpect(status().isOk());

        ArgumentCaptor<DomainEvent<?>> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(transport, org.mockito.Mockito.atLeast(2)).publish(captor.capture());
        List<DomainEvent<?>> events = captor.getAllValues();
        assertThat(events).anyMatch(e -> e.eventType().equals(YardDomainEvents.POSITION_FREED));
    }

    @Test
    void optimize_publishesOptimizationRun() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "blockId", block.getId().toString(),
                "strategy", "FIFO",
                "dryRun", true));
        performPost("/api/v1/yard/optimize", body, YardTestTokens.yardManager())
                .andExpect(status().isOk());

        ArgumentCaptor<DomainEvent<?>> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(transport, org.mockito.Mockito.atLeastOnce()).publish(captor.capture());
        List<DomainEvent<?>> events = captor.getAllValues();
        DomainEvent<?> event = events.stream()
                .filter(e -> e.eventType().equals(YardDomainEvents.OPTIMIZATION_RUN))
                .findFirst().orElseThrow();
        YardDomainEvents.OptimizationRunPayload payload =
                (YardDomainEvents.OptimizationRunPayload) event.payload();
        assertThat(payload.strategy()).isEqualTo("FIFO");
        assertThat(payload.dryRun()).isTrue();
    }

    private String tierUrl(YardTier t) {
        UUID bayId = rowRepository.findById(t.getRowId()).orElseThrow().getBayId();
        return "/api/v1/yard/blocks/" + block.getId() + "/bays/" + bayId + "/rows/" + t.getRowId()
                + "/tiers/" + t.getId() + "/container";
    }
}
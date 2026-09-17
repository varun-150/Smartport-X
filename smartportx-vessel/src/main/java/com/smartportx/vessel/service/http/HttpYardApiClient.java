package com.smartportx.vessel.service.http;

import com.smartportx.vessel.service.YardApiClient;
import com.smartportx.vessel.support.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class HttpYardApiClient implements YardApiClient {

    private static final Logger log = LoggerFactory.getLogger(HttpYardApiClient.class);

    private final RestClient restClient;

    public HttpYardApiClient(
            @Value("${smartportx.clients.yard.base-url}") String baseUrl,
            @Value("${smartportx.clients.yard.connect-timeout-ms}") long connectMs,
            @Value("${smartportx.clients.yard.read-timeout-ms}") long readMs) {
        this.restClient = HttpClients.create(baseUrl, connectMs, readMs);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void placeContainer(String blockCode, String bayCode, String rowCode, String tierCode,
                               String containerNumber, UUID containerId) {
        try {
            UUID blockId = resolveCode(restClient.get().uri("/api/v1/yard/blocks").retrieve().body(List.class),
                    "blockCode", blockCode);
            UUID bayId = resolveCode(restClient.get().uri("/api/v1/yard/blocks/{id}/bays", blockId)
                    .retrieve().body(List.class), "bayCode", bayCode);
            UUID rowId = resolveCode(restClient.get().uri("/api/v1/yard/bays/{id}/rows", bayId)
                    .retrieve().body(List.class), "rowCode", rowCode);
            UUID tierId = resolveCode(restClient.get().uri("/api/v1/yard/rows/{id}/tiers", rowId)
                    .retrieve().body(List.class), "tierCode", tierCode);

            restClient.put()
                    .uri("/api/v1/yard/blocks/{blockId}/bays/{bayId}/rows/{rowId}/tiers/{tierId}/container",
                         blockId, bayId, rowId, tierId)
                    .body(Map.of("containerId", containerId.toString(),
                                 "containerNumber", containerNumber))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Yard place OK: {}-{}-{}-{} container={}", blockCode, bayCode, rowCode, tierCode, containerNumber);
        } catch (Exception e) {
            log.warn("Best-effort yard place failed ({}-{}-{}-{} container={}): {}",
                    blockCode, bayCode, rowCode, tierCode, containerNumber, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private UUID resolveCode(List<?> items, String key, String expectedCode) {
        for (Object obj : items) {
            Map<?, ?> m = (Map<?, ?>) obj;
            if (expectedCode.equals(m.get(key))) return UUID.fromString(m.get("id").toString());
        }
        throw new IllegalArgumentException("Yard entity not found: " + key + "=" + expectedCode);
    }
}
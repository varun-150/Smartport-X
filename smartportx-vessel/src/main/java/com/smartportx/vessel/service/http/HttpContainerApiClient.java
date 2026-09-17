package com.smartportx.vessel.service.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartportx.vessel.service.ContainerApiClient;
import com.smartportx.vessel.support.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

@Component
public class HttpContainerApiClient implements ContainerApiClient {

    private static final Logger log = LoggerFactory.getLogger(HttpContainerApiClient.class);

    private final RestClient restClient;

    public HttpContainerApiClient(
            @Value("${smartportx.clients.container.base-url}") String baseUrl,
            @Value("${smartportx.clients.container.connect-timeout-ms}") long connectMs,
            @Value("${smartportx.clients.container.read-timeout-ms}") long readMs) {
        this.restClient = HttpClients.create(baseUrl, connectMs, readMs);
    }

    @Override
    public Optional<ContainerInfo> findByContainerNumber(String containerNumber) {
        try {
            JsonNode json = restClient.get()
                    .uri("/api/v1/containers/number/{cn}", containerNumber)
                    .retrieve()
                    .body(JsonNode.class);
            if (json == null) return Optional.empty();
            return Optional.of(new ContainerInfo(
                    UUID.fromString(json.get("id").asText()),
                    json.get("containerNumber").asText(),
                    json.has("containerType") ? json.get("containerType").asText() : null,
                    json.get("status").asText(),
                    json.has("weightKg") && !json.get("weightKg").isNull() ? json.get("weightKg").asDouble() : null,
                    json.has("reefer") && json.get("reefer").asBoolean()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            throw new ContainerUnavailableException("Container registry unavailable", e);
        }
    }

    @Override
    public void changeStatus(UUID containerId, String status, String location, String reason) {
        try {
            var body = new java.util.HashMap<String, Object>();
            body.put("status", status);
            if (location != null) body.put("location", location);
            if (reason != null) body.put("reason", reason);
            restClient.put()
                    .uri("/api/v1/containers/{id}/status", containerId)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Best-effort container status change failed (container={} status={}): {}",
                    containerId, status, e.getMessage());
        }
    }
}
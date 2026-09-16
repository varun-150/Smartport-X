package com.smartportx.gate.service.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartportx.gate.service.YardClient;
import com.smartportx.gate.support.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Service
@ConditionalOnMissingBean(YardClient.class)
public class HttpYardClient implements YardClient {

    private static final Logger log = LoggerFactory.getLogger(HttpYardClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpYardClient(@Value("${smartportx.clients.yard.base-url}") String baseUrl,
                          @Value("${smartportx.clients.yard.connect-timeout-ms:2000}") long connectTimeoutMs,
                          @Value("${smartportx.clients.yard.read-timeout-ms:3000}") long readTimeoutMs,
                          ObjectMapper objectMapper) {
        this.restClient = HttpClients.create(baseUrl, connectTimeoutMs, readTimeoutMs);
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<Boolean> isPositionAssigned(String containerNumber) {
        try {
            return restClient.get()
                    .uri("/api/v1/yard/containers/{containerNumber}/position", containerNumber)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 404) {
                            return Optional.of(false);
                        }
                        if (status != 200) {
                            log.warn("Yard client: yard service returned HTTP {} for {}", status, containerNumber);
                            return Optional.<Boolean>empty();
                        }
                        YardPositionResponse body = objectMapper.readValue(
                                response.getBody().readAllBytes(), YardPositionResponse.class);
                        return Optional.of(body.assigned());
                    });
        } catch (Exception e) {
            log.warn("Yard client: unable to reach yard service for {} ({}): {}",
                    containerNumber, e.getClass().getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }

    private record YardPositionResponse(boolean assigned, String position) {
    }
}
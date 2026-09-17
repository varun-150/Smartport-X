package com.smartportx.gate.service.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartportx.gate.service.ContainerClient;
import com.smartportx.gate.support.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

@Service
public class HttpContainerClient implements ContainerClient {

    private static final Logger log = LoggerFactory.getLogger(HttpContainerClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpContainerClient(@Value("${smartportx.clients.container.base-url}") String baseUrl,
                               @Value("${smartportx.clients.container.connect-timeout-ms:2000}") long connectTimeoutMs,
                               @Value("${smartportx.clients.container.read-timeout-ms:3000}") long readTimeoutMs,
                               ObjectMapper objectMapper) {
        this.restClient = HttpClients.create(baseUrl, connectTimeoutMs, readTimeoutMs);
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ContainerInfo> findByContainerNumber(String containerNumber) {
        try {
            return restClient.get()
                    .uri("/api/v1/containers/number/{containerNumber}", containerNumber)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 404) {
                            return Optional.<ContainerInfo>empty();
                        }
                        if (status != 200) {
                            log.warn("Container client: container service returned HTTP {} for {}",
                                    status, containerNumber);
                            return Optional.<ContainerInfo>empty();
                        }
                        ContainerResponse body = objectMapper.readValue(
                                response.getBody().readAllBytes(), ContainerResponse.class);
                        return Optional.of(new ContainerInfo(body.containerNumber(), body.containerType(),
                                body.weight(), body.dangerousGoods(),
                                body.status() == null ? null : body.status().toString()));
                    });
        } catch (Exception e) {
            log.warn("Container client: unable to reach container service for {} ({}): {}",
                    containerNumber, e.getClass().getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }

    private record ContainerResponse(UUID id, String containerNumber, String containerType,
                                     Object status, Double weight, boolean dangerousGoods) {
    }
}
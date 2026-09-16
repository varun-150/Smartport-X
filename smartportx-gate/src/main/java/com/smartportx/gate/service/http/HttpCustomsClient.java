package com.smartportx.gate.service.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartportx.gate.service.CustomsClient;
import com.smartportx.gate.support.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Locale;
import java.util.Optional;

@Service
@ConditionalOnMissingBean(CustomsClient.class)
public class HttpCustomsClient implements CustomsClient {

    private static final Logger log = LoggerFactory.getLogger(HttpCustomsClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpCustomsClient(@Value("${smartportx.clients.integration.base-url}") String baseUrl,
                             @Value("${smartportx.clients.integration.connect-timeout-ms:2000}") long connectTimeoutMs,
                             @Value("${smartportx.clients.integration.read-timeout-ms:3000}") long readTimeoutMs,
                             ObjectMapper objectMapper) {
        this.restClient = HttpClients.create(baseUrl, connectTimeoutMs, readTimeoutMs);
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<CustomsControlStatus> statusFor(String containerNumber) {
        try {
            return restClient.get()
                    .uri("/api/v1/customs/containers/{containerNumber}/control", containerNumber)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 404) {
                            return Optional.of(new CustomsControlStatus("PENDING"));
                        }
                        if (status != 200) {
                            log.warn("Customs client: integration service returned HTTP {} for {}",
                                    status, containerNumber);
                            return Optional.<CustomsControlStatus>empty();
                        }
                        CustomsResponse body = objectMapper.readValue(
                                response.getBody().readAllBytes(), CustomsResponse.class);
                        String value = body.status() == null ? "PENDING" : body.status().toUpperCase(Locale.ROOT);
                        return Optional.of(new CustomsControlStatus(value));
                    });
        } catch (Exception e) {
            log.warn("Customs client: unable to reach integration service for {} ({}): {}",
                    containerNumber, e.getClass().getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }

    private record CustomsResponse(String containerNumber, String status, String containerType) {
    }
}
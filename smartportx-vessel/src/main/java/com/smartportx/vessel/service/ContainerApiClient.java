package com.smartportx.vessel.service;

import com.smartportx.common.error.ApiException;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

/**
 * Fail-secure container registry client. 404 means "not registered";
 * transport errors throw {@link ContainerUnavailableException} (fail-closed
 * at the caller). See D-448 (offline Resilience4j starter absent — plain
 * RestClient substitution, documented like WireMock in gate M6).
 */
public interface ContainerApiClient {

    Optional<ContainerInfo> findByContainerNumber(String containerNumber);

    void changeStatus(UUID containerId, String status, String location, String reason);

    record ContainerInfo(UUID id, String containerNumber, String containerType,
                         String status, Double weightKg, boolean reefer) {
    }

    class ContainerUnavailableException extends RuntimeException {
        public ContainerUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
package com.smartportx.integration.service;

import com.smartportx.common.correlation.CorrelationContext;
import com.smartportx.integration.domain.IntegrationLog;
import com.smartportx.integration.repository.IntegrationLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Append-only audit trail (M8.8) for partner-facing operations. Writes are isolated in
 * their own transaction so a failed business call is still recorded.
 */
@Service
public class IntegrationAuditService {

    private final IntegrationLogRepository repository;

    public IntegrationAuditService(IntegrationLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String serviceName, String operation, String status,
                       Map<String, Object> request, Map<String, Object> response, String error) {
        IntegrationLog log = new IntegrationLog();
        log.setServiceName(serviceName);
        log.setOperation(operation);
        log.setStatus(status);
        log.setRequestData(request);
        log.setResponseData(response);
        log.setErrorMessage(error);
        log.setCorrelationId(UUID.fromString(CorrelationContext.getOrCreate()));
        repository.save(log);
    }
}

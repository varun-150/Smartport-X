package com.smartportx.identity.security;

import com.smartportx.common.correlation.CorrelationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AuditLog {

    private static final Logger log = LoggerFactory.getLogger(AuditLog.class);

    public void log(String action, String principal, String sourceIp, String outcome, String detail) {
        String correlationId = CorrelationContext.current().orElse("n/a");
        log.info("AUDIT action={} principal={} sourceIp={} outcome={} detail={} correlationId={}",
                action, principal, sourceIp, outcome, detail, correlationId);
    }
}
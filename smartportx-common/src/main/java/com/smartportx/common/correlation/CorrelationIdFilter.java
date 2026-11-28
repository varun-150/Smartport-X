package com.smartportx.common.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Assigns or propagates {@code X-Correlation-Id}: takes the inbound header value when
 * present, otherwise generates a UUID; echoes it on the response header and exposes it
 * via {@link CorrelationContext} (MDC included) for the request duration.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(CorrelationContext.HEADER);
        String id = (incoming != null && !incoming.isBlank()) ? incoming : CorrelationContext.uuid();
        CorrelationContext.set(id);
        try {
            response.setHeader(CorrelationContext.HEADER, id);
            chain.doFilter(request, response);
        } finally {
            CorrelationContext.clear();
        }
    }
}
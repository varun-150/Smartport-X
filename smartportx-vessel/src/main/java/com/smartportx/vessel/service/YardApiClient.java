package com.smartportx.vessel.service;

import com.smartportx.common.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Yard staging client. Calls are advisory-best-effort (fail-open, logged)
 * because yard assignment in M7 is triggered by a manual operator action
 * and upstream propagation is a side-effect (D-448).
 */
public interface YardApiClient {

    void placeContainer(String block, String bay, String row, String tier,
                        String containerNumber, UUID containerId);

    class YardUnavailableException extends RuntimeException {
        public YardUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
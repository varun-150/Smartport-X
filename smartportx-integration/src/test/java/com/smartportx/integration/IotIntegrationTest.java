package com.smartportx.integration;

import com.smartportx.integration.domain.events.IntegrationDomainEvents;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class IotIntegrationTest extends BaseIntegrationTest {

    private String reading(String container, String sensor, String value, Integer battery) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("containerNumber", container);
        body.put("sensorType", sensor);
        body.put("value", value);
        if (battery != null) {
            body.put("batteryLevel", battery);
        }
        return json(body);
    }

    @Test
    void criticalTemperatureCrossesThresholdAndAlerts() throws Exception {
        doPost("/api/v1/integration/iot/data", reading("MSKU1234567", "TEMPERATURE", "42.5", null),
                IntegrationTestTokens.admin())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alert").value(org.hamcrest.Matchers.containsString("critical")));

        assertThat(iotReadings.count()).isEqualTo(1);
        assertThat(hasEvent(IntegrationDomainEvents.IOT_ALERT_TRIGGERED)).isTrue();
        assertThat(payloadOfType(IntegrationDomainEvents.IOT_ALERT_TRIGGERED,
                IntegrationDomainEvents.IotAlertPayload.class))
                .get()
                .satisfies(p -> {
                    assertThat(p.sensorType()).isEqualTo("TEMPERATURE");
                    assertThat(p.alertLevel()).isEqualTo("CRITICAL");
                });
    }

    @Test
    void normalTemperatureStoredWithoutAlert() throws Exception {
        doPost("/api/v1/integration/iot/data", reading("MSKU1234567", "TEMPERATURE", "20.0", null),
                IntegrationTestTokens.terminalOp())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alert").doesNotExist());

        assertThat(hasEvent(IntegrationDomainEvents.IOT_ALERT_TRIGGERED)).isFalse();
        assertThat(iotReadings.count()).isEqualTo(1);
    }

    @Test
    void lowBatteryTriggersWarning() throws Exception {
        doPost("/api/v1/integration/iot/data", reading("CMAU0000425", "BATTERY", "5", 5),
                IntegrationTestTokens.admin())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alert").value(org.hamcrest.Matchers.containsString("Low battery")));

        assertThat(hasEvent(IntegrationDomainEvents.IOT_ALERT_TRIGGERED)).isTrue();
        assertThat(payloadOfType(IntegrationDomainEvents.IOT_ALERT_TRIGGERED,
                IntegrationDomainEvents.IotAlertPayload.class))
                .get()
                .satisfies(p -> assertThat(p.alertLevel()).isEqualTo("WARNING"));
    }

    @Test
    void nonNumericValueStoredAsTextWithoutAlert() throws Exception {
        doPost("/api/v1/integration/iot/data", reading("MSKU1234567", "GPS", "40.7N,74.0W", null),
                IntegrationTestTokens.admin())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.value").value("40.7N,74.0W"));

        assertThat(hasEvent(IntegrationDomainEvents.IOT_ALERT_TRIGGERED)).isFalse();
    }

    @Test
    void unauthenticatedIngestRejected() throws Exception {
        doPost("/api/v1/integration/iot/data", reading("MSKU1234567", "TEMPERATURE", "20.0", null), null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void plainUserCannotIngest() throws Exception {
        doPost("/api/v1/integration/iot/data", reading("MSKU1234567", "TEMPERATURE", "20.0", null),
                IntegrationTestTokens.plain())
                .andExpect(status().isForbidden());
    }
}

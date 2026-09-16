package com.smartportx.integration.service;

import com.smartportx.common.correlation.CorrelationContext;
import com.smartportx.common.event.DomainEvent;
import com.smartportx.integration.domain.IotReading;
import com.smartportx.integration.domain.events.IntegrationDomainEvents;
import com.smartportx.integration.event.EventTransport;
import com.smartportx.integration.repository.IotReadingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Telemetry ingestion (FR-011). Persists a reading and evaluates sensor thresholds,
 * emitting an {@code integration.IotAlertTriggered} event (warning or critical level) when
 * a bound is crossed. Thresholds are config-driven (smartportx.integration.iot.*).
 */
@Service
public class IotIngestService {

    private static final Logger log = LoggerFactory.getLogger(IotIngestService.class);

    public record ReadingInput(String containerNumber, String sensorType, String value,
                               Instant timestamp, Double latitude, Double longitude,
                               Integer batteryLevel, Integer signalStrength) {
    }

    public record IngestResult(IotReading reading, String alert) {
        public static IngestResult of(IotReading reading, String alert) {
            return new IngestResult(reading, alert);
        }
    }

    private final IotReadingRepository readings;
    private final EventTransport transport;
    private final IntegrationAuditService audit;
    private final String source;
    private final double temperatureWarning;
    private final double temperatureCritical;
    private final double shockWarning;
    private final double shockCritical;
    private final int lowBatteryPct;

    public IotIngestService(IotReadingRepository readings,
                            EventTransport transport,
                            IntegrationAuditService audit,
                            @Value("${smartportx.integration.source:smartportx-integration}") String source,
                            @Value("${smartportx.integration.iot.temperature-warning-celsius:35.0}") double temperatureWarning,
                            @Value("${smartportx.integration.iot.temperature-critical-celsius:40.0}") double temperatureCritical,
                            @Value("${smartportx.integration.iot.shock-warning-g:10.0}") double shockWarning,
                            @Value("${smartportx.integration.iot.shock-critical-g:20.0}") double shockCritical,
                            @Value("${smartportx.integration.iot.low-battery-pct:15}") int lowBatteryPct) {
        this.readings = readings;
        this.transport = transport;
        this.audit = audit;
        this.source = source;
        this.temperatureWarning = temperatureWarning;
        this.temperatureCritical = temperatureCritical;
        this.shockWarning = shockWarning;
        this.shockCritical = shockCritical;
        this.lowBatteryPct = lowBatteryPct;
    }

    @Transactional
    public IotReading ingest(ReadingInput input) {
        return ingestResult(input).reading();
    }

    @Transactional
    public IngestResult ingestResult(ReadingInput input) {
        IotReading entity = new IotReading();
        entity.setContainerNumber(input.containerNumber());
        entity.setSensorType(normalizeSensor(input.sensorType()));
        entity.setValue(input.value());
        parseNumeric(input, entity);
        entity.setUnit(unitFor(input.sensorType()));
        entity.setLatitude(input.latitude());
        entity.setLongitude(input.longitude());
        entity.setSignalStrength(input.signalStrength());
        entity.setBatteryLevel(input.batteryLevel());
        entity.setDeviceTimestamp(input.timestamp());
        entity.setTimestamp(input.timestamp() == null ? Instant.now() : input.timestamp());
        IotReading saved = readings.save(entity);

        String alert = evaluate(saved);
        if (alert != null) {
            String level = alertLevel(saved);
            transport.publish(DomainEvent.of(IntegrationDomainEvents.IOT_ALERT_TRIGGERED, source,
                    new IntegrationDomainEvents.IotAlertPayload(
                            saved.getContainerNumber(), saved.getSensorType(), level, alert,
                            saved.getTimestamp())));
            audit.record(source, "iot.alert", "TRIGGERED",
                    Map.of("containerNumber", saved.getContainerNumber(), "sensorType", saved.getSensorType()),
                    Map.of("alertLevel", level, "message", alert), null);
        }
        audit.record(source, "iot.ingest", "SUCCESS",
                Map.of("containerNumber", saved.getContainerNumber(), "sensorType", saved.getSensorType()),
                Map.of("readingId", saved.getId().toString()), null);
        return IngestResult.of(saved, alert);
    }

    private void parseNumeric(ReadingInput input, IotReading entity) {
        if (input.value() == null || input.value().isBlank()) {
            return;
        }
        String trimmed = input.value().trim();
        try {
            entity.setValueNumeric(Double.parseDouble(trimmed));
        } catch (NumberFormatException e) {
            entity.setValueText(trimmed);
            entity.setValueNumeric(null);
        }
    }

    private String evaluate(IotReading reading) {
        Double v = reading.getValueNumeric();
        if (v == null) {
            return null;
        }
        switch (reading.getSensorType()) {
            case "TEMPERATURE":
                if (v >= temperatureCritical) {
                    return String.format(Locale.ROOT, "Temperature critical: %.1f C >= %.1f C",
                            v, temperatureCritical);
                }
                if (v >= temperatureWarning) {
                    return String.format(Locale.ROOT, "Temperature warning: %.1f C >= %.1f C",
                            v, temperatureWarning);
                }
                return null;
            case "SHOCK":
                if (v >= shockCritical) {
                    return String.format(Locale.ROOT, "Shock critical: %.1f g >= %.1f g", v, shockCritical);
                }
                if (v >= shockWarning) {
                    return String.format(Locale.ROOT, "Shock warning: %.1f g >= %.1f g", v, shockWarning);
                }
                return null;
            case "BATTERY":
                if (reading.getBatteryLevel() != null && reading.getBatteryLevel() <= lowBatteryPct) {
                    return String.format(Locale.ROOT, "Low battery: %d%% <= %d%%",
                            reading.getBatteryLevel(), lowBatteryPct);
                }
                return null;
            default:
                return null;
        }
    }

    private String alertLevel(IotReading reading) {
        Double v = reading.getValueNumeric();
        if (v == null) {
            return "WARNING";
        }
        boolean critical = switch (reading.getSensorType()) {
            case "TEMPERATURE" -> v >= temperatureCritical;
            case "SHOCK" -> v >= shockCritical;
            default -> false;
        };
        return critical ? "CRITICAL" : "WARNING";
    }

    private String normalizeSensor(String sensorType) {
        return sensorType == null ? "UNKNOWN" : sensorType.trim().toUpperCase(Locale.ROOT);
    }

    private String unitFor(String sensorType) {
        if (sensorType == null) {
            return null;
        }
        return switch (sensorType.trim().toUpperCase(Locale.ROOT)) {
            case "TEMPERATURE" -> "C";
            case "SHOCK" -> "g";
            case "GPS" -> "deg";
            default -> null;
        };
    }
}

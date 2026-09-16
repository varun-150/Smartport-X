package com.smartportx.integration.repository;

import com.smartportx.integration.domain.IotReading;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface IotReadingRepository extends JpaRepository<IotReading, UUID> {

    List<IotReading> findByContainerNumberAndSensorTypeOrderByTimestampAsc(
            String containerNumber, String sensorType);

    /** Telemetry observed since a cutoff (used for freshness/threshold evaluation). */
    List<IotReading> findTop20ByContainerNumberOrderByTimestampDesc(String containerNumber);

    List<IotReading> findByTimestampAfter(Instant cutoff);
}

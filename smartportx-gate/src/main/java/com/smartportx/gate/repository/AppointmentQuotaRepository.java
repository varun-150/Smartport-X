package com.smartportx.gate.repository;

import com.smartportx.gate.domain.AppointmentQuota;
import com.smartportx.gate.domain.AppointmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentQuotaRepository extends JpaRepository<AppointmentQuota, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AppointmentQuota> findByTimeWindowStartAndTimeWindowEndAndAppointmentTypeAndGateLane(
            Instant windowStart, Instant windowEnd, AppointmentType appointmentType, String gateLane);

    List<AppointmentQuota> findByTimeWindowStartBetweenOrderByTimeWindowStartAsc(Instant from, Instant to);

    Optional<AppointmentQuota> findByTimeWindowStartAndTimeWindowEndAndAppointmentType(Instant windowStart,
            Instant windowEnd, AppointmentType appointmentType);
}
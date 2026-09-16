package com.smartportx.gate.repository;

import com.smartportx.gate.domain.GateTransaction;
import com.smartportx.gate.domain.GateTransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GateTransactionRepository extends JpaRepository<GateTransaction, UUID> {

    Optional<GateTransaction> findFirstByAppointmentIdAndStatusOrderByCheckInTimeDesc(UUID appointmentId,
            GateTransactionStatus status);

    boolean existsByAppointmentIdAndStatus(UUID appointmentId, GateTransactionStatus status);

    Optional<GateTransaction> findFirstByEirNumberStartingWithOrderByEirNumberDesc(String prefix);

    List<GateTransaction> findByStatus(GateTransactionStatus status);

    List<GateTransaction> findByStatusAndGateLane(GateTransactionStatus status, String gateLane);

    long countByStatus(GateTransactionStatus status);

    long countByStatusAndCheckInTimeAfter(GateTransactionStatus status, Instant after);

    List<GateTransaction> findByStatusOrderByCheckInTimeAsc(GateTransactionStatus status);
}
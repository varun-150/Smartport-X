package com.smartportx.vessel.repository;

import com.smartportx.vessel.domain.DischargePlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DischargePlanRepository extends JpaRepository<DischargePlan, UUID> {

    List<DischargePlan> findByVesselIdOrderByCreatedAtDesc(UUID vesselId);

    Optional<DischargePlan> findFirstByVesselIdOrderByCreatedAtDesc(UUID vesselId);

    Optional<DischargePlan> findByVesselIdAndVoyageNumber(UUID vesselId, String voyageNumber);
}
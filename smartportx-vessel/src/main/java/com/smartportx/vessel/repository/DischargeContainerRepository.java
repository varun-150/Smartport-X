package com.smartportx.vessel.repository;

import com.smartportx.vessel.domain.DischargeContainer;
import com.smartportx.vessel.domain.DischargeContainerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DischargeContainerRepository extends JpaRepository<DischargeContainer, UUID> {

    List<DischargeContainer> findByDischargePlanIdOrderByVesselPosition(UUID dischargePlanId);

    @Query("SELECT c FROM DischargeContainer c, DischargePlan p " +
           "WHERE c.dischargePlanId = p.id AND p.vesselId = :vesselId AND c.containerNumber = :containerNumber " +
           "ORDER BY p.createdAt DESC")
    List<DischargeContainer> findByVesselAndContainer(@Param("vesselId") UUID vesselId,
                                                      @Param("containerNumber") String containerNumber);

    @Query("SELECT c FROM DischargeContainer c, DischargePlan p " +
           "WHERE c.dischargePlanId = p.id AND p.vesselId = :vesselId ORDER BY p.createdAt DESC, c.vesselPosition")
    List<DischargeContainer> findByVesselId(@Param("vesselId") UUID vesselId);

    @Query("SELECT c.status, COUNT(c) FROM DischargeContainer c, DischargePlan p " +
           "WHERE c.dischargePlanId = p.id AND p.id = :planId GROUP BY c.status")
    List<Object[]> countByStatusForPlan(@Param("planId") UUID planId);
}
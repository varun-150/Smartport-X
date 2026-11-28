package com.smartportx.vessel.repository;

import com.smartportx.vessel.domain.VesselSchedule;
import com.smartportx.vessel.domain.VesselScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface VesselScheduleRepository extends JpaRepository<VesselSchedule, UUID> {

    List<VesselSchedule> findByVesselIdOrderByEtaDesc(UUID vesselId);

    @Query("SELECT s FROM VesselSchedule s WHERE s.vesselId = :vesselId AND s.status IN (:statuses) ORDER BY s.eta ASC")
    List<VesselSchedule> findByVesselIdAndStatusIn(@Param("vesselId") UUID vesselId,
                                                   @Param("statuses") Set<VesselScheduleStatus> statuses);

    @Query("SELECT s FROM VesselSchedule s WHERE s.status IN (:statuses) ORDER BY s.eta ASC")
    List<VesselSchedule> findByStatusIn(@Param("statuses") Set<VesselScheduleStatus> statuses);

    List<VesselSchedule> findByEtaBetween(Instant from, Instant to);
}
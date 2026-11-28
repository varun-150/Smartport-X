package com.smartportx.vessel.repository;

import com.smartportx.vessel.domain.Vessel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VesselRepository extends JpaRepository<Vessel, UUID> {

    Optional<Vessel> findByImoNumber(String imoNumber);

    boolean existsByImoNumber(String imoNumber);
}
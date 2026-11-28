package com.smartportx.vessel.service;

import com.smartportx.common.error.ApiException;
import com.smartportx.vessel.domain.Vessel;
import com.smartportx.vessel.domain.VesselType;
import com.smartportx.vessel.repository.VesselRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class VesselService {

    private final VesselRepository vessels;

    public VesselService(VesselRepository vessels) {
        this.vessels = vessels;
    }

    @Transactional
    public Vessel register(String imoNumber, String vesselName, VesselType type,
                           double lengthM, double beamM, double draftM,
                           Integer teuCapacity, Double grossTonnage,
                           String flag, String operator, String callSign) {
        if (vessels.existsByImoNumber(imoNumber)) {
            throw new ApiException(409, "already_registered", "A vessel with IMO number " + imoNumber + " exists");
        }
        Vessel v = new Vessel();
        v.setImoNumber(imoNumber);
        v.setVesselName(vesselName);
        v.setVesselType(type);
        v.setLengthM(lengthM);
        v.setBeamM(beamM);
        v.setDraftM(draftM);
        v.setTeuCapacity(teuCapacity);
        v.setGrossTonnage(grossTonnage);
        v.setFlag(flag);
        v.setOperator(operator);
        v.setCallSign(callSign);
        return vessels.save(v);
    }

    @Transactional(readOnly = true)
    public Vessel get(UUID id) {
        return vessels.findById(id)
                .orElseThrow(() -> new ApiException(404, "vessel_not_found", "Vessel not found"));
    }

    @Transactional(readOnly = true)
    public List<Vessel> list() {
        return vessels.findAll();
    }

    @Transactional
    public Vessel update(UUID id, String imoNumber, String vesselName, VesselType type,
                         double lengthM, double beamM, double draftM,
                         Integer teuCapacity, Double grossTonnage,
                         String flag, String operator, String callSign) {
        Vessel v = get(id);
        vessels.findByImoNumber(imoNumber)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ApiException(409, "already_registered",
                            "A vessel with IMO number " + imoNumber + " exists");
                });
        v.setImoNumber(imoNumber);
        v.setVesselName(vesselName);
        v.setVesselType(type);
        v.setLengthM(lengthM);
        v.setBeamM(beamM);
        v.setDraftM(draftM);
        v.setTeuCapacity(teuCapacity);
        v.setGrossTonnage(grossTonnage);
        v.setFlag(flag);
        v.setOperator(operator);
        v.setCallSign(callSign);
        return vessels.save(v);
    }

    @Transactional
    public void delete(UUID id) {
        vessels.delete(get(id));
    }
}
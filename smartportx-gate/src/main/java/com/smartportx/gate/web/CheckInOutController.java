package com.smartportx.gate.web;

import com.smartportx.gate.service.GateProcessingService;
import com.smartportx.gate.support.GateIds;
import com.smartportx.gate.web.dto.GateDtos;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/gate")
public class CheckInOutController {

    private final GateProcessingService processing;

    public CheckInOutController(GateProcessingService processing) {
        this.processing = processing;
    }

    @PostMapping("/check-in")
    @PreAuthorize("hasAnyRole('GATE_OPERATOR','GATE_SUPERVISOR','ADMIN')")
    public ResponseEntity<GateDtos.CheckInResponse> checkIn(
            @RequestBody GateDtos.CheckInRequest request, Authentication authentication) {
        UUID appointmentId = GateIds.toUuid(request.appointmentId(), GateIds.APPOINTMENT_PREFIX);
        GateProcessingService.CheckInResult result = processing.checkIn(
                appointmentId, toCommand(request), verifiedBy(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(GateDtos.CheckInResponse.from(result));
    }

    @PostMapping("/check-out")
    @PreAuthorize("hasAnyRole('GATE_OPERATOR','GATE_SUPERVISOR','ADMIN')")
    public ResponseEntity<GateDtos.CheckOutResponse> checkOut(
            @RequestBody GateDtos.CheckOutRequest request, Authentication authentication) {
        UUID transactionId = GateIds.toUuid(request.transactionId(), GateIds.TRANSACTION_PREFIX);
        GateProcessingService.CheckOutResult result = processing.checkOut(
                transactionId, toOutCommand(request), verifiedBy(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(GateDtos.CheckOutResponse.from(result));
    }

    private GateProcessingService.CheckInCommand toCommand(GateDtos.CheckInRequest request) {
        return new GateProcessingService.CheckInCommand(
                request.truckLicensePlate(), request.driverName(), request.driverLicense(),
                request.containerNumber(), request.sealNumber(), request.truckWeight(),
                request.documents(), request.notes());
    }

    private GateProcessingService.CheckOutCommand toOutCommand(GateDtos.CheckOutRequest request) {
        return new GateProcessingService.CheckOutCommand(
                request.truckLicensePlate(), request.driverName(), request.driverLicense(),
                request.containerNumber(), request.sealIntact(), request.newSealNumber(),
                request.truckWeight(), request.notes());
    }

    private String verifiedBy(Authentication authentication) {
        return authentication != null && authentication.getName() != null
                ? authentication.getName() : "gate-operator-terminal";
    }
}
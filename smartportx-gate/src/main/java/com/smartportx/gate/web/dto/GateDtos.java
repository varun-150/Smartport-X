package com.smartportx.gate.web.dto;

import com.smartportx.gate.service.GateProcessingService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Gate check-in / check-out contracts (API-CONTRACTS &sect;6.3). Wire ids are
 * prefixed ({@code apt-<uuid>}, {@code txn-<uuid>}).
 */
public final class GateDtos {

    private GateDtos() {
    }

    public record CheckInRequest(String appointmentId, String truckLicensePlate, String driverName,
                                 String driverLicense, String containerNumber, String sealNumber,
                                 Double truckWeight, List<String> documents, String notes) {
    }

    public record CheckInResponse(String transactionId, String appointmentId, String confirmationNumber,
                                  String status, String gateLane, Instant checkInTime, String yardLocation,
                                  Instant estimatedServiceTime, List<DocumentResponse> documents,
                                  String instructions) {

        public static CheckInResponse from(GateProcessingService.CheckInResult result) {
            return new CheckInResponse(
                    "txn-" + result.transactionId(),
                    "apt-" + result.appointmentId(),
                    result.confirmationNumber(),
                    result.status(),
                    result.gateLane(),
                    result.checkInTime(),
                    result.yardLocation(),
                    result.estimatedServiceTime(),
                    result.documents().stream().map(DocumentResponse::from).toList(),
                    result.instructions());
        }
    }

    public record CheckOutRequest(String transactionId, String appointmentId, String truckLicensePlate,
                                  String driverName, String driverLicense, String containerNumber,
                                  Boolean sealIntact, String newSealNumber, Double truckWeight, String notes) {
    }

    public record CheckOutResponse(String transactionId, String appointmentId, String confirmationNumber,
                                   String status, String eirNumber, Instant checkInTime, Instant checkOutTime,
                                   String gateLane, String yardLocation, String verifiedBy) {

        public static CheckOutResponse from(GateProcessingService.CheckOutResult result) {
            return new CheckOutResponse(
                    "txn-" + result.transactionId(),
                    "apt-" + result.appointmentId(),
                    result.confirmationNumber(),
                    result.status(),
                    result.eirNumber(),
                    result.checkInTime(),
                    result.checkOutTime(),
                    result.gateLane(),
                    result.yardLocation(),
                    result.verifiedBy());
        }
    }

    public record DocumentResponse(String id, String name, String status, Instant verifiedAt, String verifiedBy) {

        public static DocumentResponse from(GateProcessingService.DocumentResult document) {
            return new DocumentResponse(document.id(), document.name(), document.status(),
                    document.verifiedAt(), document.verifiedBy());
        }
    }
}
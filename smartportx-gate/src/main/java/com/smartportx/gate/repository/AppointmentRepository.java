package com.smartportx.gate.repository;

import com.smartportx.gate.domain.Appointment;
import com.smartportx.gate.domain.AppointmentStatus;
import com.smartportx.gate.domain.AppointmentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    Optional<Appointment> findByConfirmationNumber(String confirmationNumber);

    Optional<Appointment> findFirstByConfirmationNumberStartingWithOrderByConfirmationNumberDesc(String prefix);

    List<Appointment> findByPreferredTimeSlotBetweenAndStatusIn(Instant from, Instant to, Collection<AppointmentStatus> statuses);

    List<Appointment> findByPreferredTimeSlotGreaterThanEqualAndStatusInOrderByPreferredTimeSlotAsc(
            Instant from, Collection<AppointmentStatus> statuses);

    long countByPreferredTimeSlotBetweenAndStatusIn(Instant from, Instant to, Collection<AppointmentStatus> statuses);

    List<Appointment> findAllByStatusIn(Collection<AppointmentStatus> statuses);

    List<Appointment> findAllByAppointmentType(AppointmentType type);

    List<Appointment> findAllByTruckingCompany(String truckingCompany);

    List<Appointment> findAllByPreferredTimeSlotBetween(Instant from, Instant to);
}
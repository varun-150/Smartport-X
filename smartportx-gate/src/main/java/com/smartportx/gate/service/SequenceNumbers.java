package com.smartportx.gate.service;

import com.smartportx.gate.repository.AppointmentRepository;
import com.smartportx.gate.repository.GateTransactionRepository;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * Human-readable booking and EIR numbers (API-CONTRACTS &sect;6).
 * Confirmation: {@code APT-YYYYMMDD-NNNN} (booking date). EIR: {@code EIR-YYYYMMDD-NNNN}
 * (check-out date). Sequences are per-day, derived from the highest existing suffix.
 */
@Service
public class SequenceNumbers {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AppointmentRepository appointments;
    private final GateTransactionRepository transactions;
    private final GateClock clock;

    public SequenceNumbers(AppointmentRepository appointments,
                           GateTransactionRepository transactions,
                           GateClock clock) {
        this.appointments = appointments;
        this.transactions = transactions;
        this.clock = clock;
    }

    public String nextConfirmationNumber() {
        String prefix = "APT-" + clock.todayUtc().format(DAY) + "-";
        int seq = appointments.findFirstByConfirmationNumberStartingWithOrderByConfirmationNumberDesc(prefix)
                .map(a -> parseSeq(a.getConfirmationNumber(), prefix))
                .orElse(0);
        return prefix + pad(seq + 1);
    }

    public String nextEirNumber() {
        String prefix = "EIR-" + clock.todayUtc().format(DAY) + "-";
        int seq = transactions.findFirstByEirNumberStartingWithOrderByEirNumberDesc(prefix)
                .map(t -> parseSeq(t.getEirNumber(), prefix))
                .orElse(0);
        return prefix + pad(seq + 1);
    }

    private static int parseSeq(String value, String prefix) {
        try {
            return Integer.parseInt(value.substring(prefix.length()));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static String pad(int value) {
        return String.format("%04d", value);
    }
}
package com.smartportx.gate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

/**
 * Gate operational rules (API-CONTRACTS &sect;6 + PHASE-4-DECISIONS D-446).
 */
@Component
@ConfigurationProperties(prefix = "smartportx.gate")
public class GateProperties {

    private String source = "smartportx-gate";
    private LocalTime operatingHoursStart = LocalTime.of(6, 0);
    private LocalTime operatingHoursEnd = LocalTime.of(22, 0);
    private int windowMinutes = 15;
    private int graceMinutes = 120;
    private int maxBookAheadDays = 14;
    private int minLeadHours = 2;
    private int defaultWindowCapacity = 25;
    private double swingWeightKg = 2500;
    private double bookingEstimateFee = 50000;
    private int averageWaitMinutes = 15;
    private List<String> activeLanes = List.of("GATE-01", "GATE-02");
    private List<String> maintenanceLanes = List.of("GATE-03");

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalTime getOperatingHoursStart() {
        return operatingHoursStart;
    }

    public void setOperatingHoursStart(LocalTime operatingHoursStart) {
        this.operatingHoursStart = operatingHoursStart;
    }

    public LocalTime getOperatingHoursEnd() {
        return operatingHoursEnd;
    }

    public void setOperatingHoursEnd(LocalTime operatingHoursEnd) {
        this.operatingHoursEnd = operatingHoursEnd;
    }

    public int getWindowMinutes() {
        return windowMinutes;
    }

    public void setWindowMinutes(int windowMinutes) {
        this.windowMinutes = windowMinutes;
    }

    public int getGraceMinutes() {
        return graceMinutes;
    }

    public void setGraceMinutes(int graceMinutes) {
        this.graceMinutes = graceMinutes;
    }

    public int getMaxBookAheadDays() {
        return maxBookAheadDays;
    }

    public void setMaxBookAheadDays(int maxBookAheadDays) {
        this.maxBookAheadDays = maxBookAheadDays;
    }

    public int getMinLeadHours() {
        return minLeadHours;
    }

    public void setMinLeadHours(int minLeadHours) {
        this.minLeadHours = minLeadHours;
    }

    public int getDefaultWindowCapacity() {
        return defaultWindowCapacity;
    }

    public void setDefaultWindowCapacity(int defaultWindowCapacity) {
        this.defaultWindowCapacity = defaultWindowCapacity;
    }

    public double getSwingWeightKg() {
        return swingWeightKg;
    }

    public void setSwingWeightKg(double swingWeightKg) {
        this.swingWeightKg = swingWeightKg;
    }

    public double getBookingEstimateFee() {
        return bookingEstimateFee;
    }

    public void setBookingEstimateFee(double bookingEstimateFee) {
        this.bookingEstimateFee = bookingEstimateFee;
    }

    public int getAverageWaitMinutes() {
        return averageWaitMinutes;
    }

    public void setAverageWaitMinutes(int averageWaitMinutes) {
        this.averageWaitMinutes = averageWaitMinutes;
    }

    public List<String> getActiveLanes() {
        return activeLanes;
    }

    public void setActiveLanes(List<String> activeLanes) {
        this.activeLanes = activeLanes;
    }

    public List<String> getMaintenanceLanes() {
        return maintenanceLanes;
    }

    public void setMaintenanceLanes(List<String> maintenanceLanes) {
        this.maintenanceLanes = maintenanceLanes;
    }
}
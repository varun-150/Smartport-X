package com.smartportx.vessel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code smartportx.vessel.*} settings. &#35; moves-per-hour drives the estimated
 * discharge duration; verify-containers-at-planning turns the planning-time
 * container-registry verification on/off (fail-closed when enabled).
 */
@ConfigurationProperties(prefix = "smartportx.vessel")
public class VesselProperties {

    private String source = "smartportx-vessel";
    private double movesPerHour = 25;
    private boolean verifyContainersAtPlanning = true;
    private long containerConnectTimeoutMs = 2000;
    private long containerReadTimeoutMs = 3000;
    private long yardConnectTimeoutMs = 2000;
    private long yardReadTimeoutMs = 3000;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public double getMovesPerHour() {
        return movesPerHour;
    }

    public void setMovesPerHour(double movesPerHour) {
        this.movesPerHour = movesPerHour;
    }

    public boolean isVerifyContainersAtPlanning() {
        return verifyContainersAtPlanning;
    }

    public void setVerifyContainersAtPlanning(boolean verifyContainersAtPlanning) {
        this.verifyContainersAtPlanning = verifyContainersAtPlanning;
    }

    public long getContainerConnectTimeoutMs() {
        return containerConnectTimeoutMs;
    }

    public void setContainerConnectTimeoutMs(long containerConnectTimeoutMs) {
        this.containerConnectTimeoutMs = containerConnectTimeoutMs;
    }

    public long getContainerReadTimeoutMs() {
        return containerReadTimeoutMs;
    }

    public void setContainerReadTimeoutMs(long containerReadTimeoutMs) {
        this.containerReadTimeoutMs = containerReadTimeoutMs;
    }

    public long getYardConnectTimeoutMs() {
        return yardConnectTimeoutMs;
    }

    public void setYardConnectTimeoutMs(long yardConnectTimeoutMs) {
        this.yardConnectTimeoutMs = yardConnectTimeoutMs;
    }

    public long getYardReadTimeoutMs() {
        return yardReadTimeoutMs;
    }

    public void setYardReadTimeoutMs(long yardReadTimeoutMs) {
        this.yardReadTimeoutMs = yardReadTimeoutMs;
    }
}
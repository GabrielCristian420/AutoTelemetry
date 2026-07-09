package com.gabrielbicu.telemetry.dto;

public interface VehicleStatsProjection {
    Double getAvgSpeedKmh();
    Integer getMaxRpm();
    /**
     * Total percentage points of fuel level consumed (calculated by accumulating
     * sequential fuel percentage drops, excluding refueling events).
     */
    Double getTotalFuelDropPct();
    Long getActiveDtcCount();
}

package com.gabrielbicu.telemetry.dto;

public interface VehicleStatsProjection {
    Double getAvgSpeedKmh();
    Integer getMaxRpm();
    Double getTotalFuelConsumed();
    Long getActiveDtcCount();
}

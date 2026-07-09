package com.gabrielbicu.telemetry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleStatsResponse {
    private Double avgSpeedKmh;
    private Integer maxRpm;
    /**
     * Total percentage points of fuel level consumed (calculated by accumulating
     * sequential fuel percentage drops, excluding refueling events).
     */
    private Double totalFuelDropPct;
    private Long activeDtcCount;
}

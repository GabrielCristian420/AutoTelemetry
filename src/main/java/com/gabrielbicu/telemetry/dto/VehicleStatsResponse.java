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
    private Double totalFuelConsumed;
    private Long activeDtcCount;
}

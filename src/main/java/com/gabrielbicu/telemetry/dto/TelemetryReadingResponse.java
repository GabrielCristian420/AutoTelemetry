package com.gabrielbicu.telemetry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * Response body for telemetry reading endpoints.
 *
 * <p>{@code dtcCodes} is returned as a list of code strings (not DtcCode
 * entities) to keep the API contract flat and stable: clients want to know
 * "which codes were active", not the internal id/createdAt of the reference rows.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelemetryReadingResponse {

    private Long id;
    private Long tripId;
    private Instant recordedAt;
    private Double speedKmh;
    private Integer rpm;
    private Double engineTempC;
    private Double fuelLevelPct;
    private Double lat;
    private Double lng;
    private Instant createdAt;
    private List<String> dtcCodes;
}

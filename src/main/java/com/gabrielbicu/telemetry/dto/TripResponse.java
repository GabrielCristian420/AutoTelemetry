package com.gabrielbicu.telemetry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Response body for trip endpoints.
 *
 * <p>{@code endedAt} and {@code distanceKm} are nullable while the trip is in
 * progress; they are populated when {@code POST /api/trips/{id}/end} is called.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripResponse {

    private Long id;
    private Long vehicleId;
    private Instant startedAt;
    private Instant endedAt;
    private Double distanceKm;
    private Instant createdAt;
}

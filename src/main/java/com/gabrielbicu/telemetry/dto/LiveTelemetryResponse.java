package com.gabrielbicu.telemetry.dto;

import java.time.Instant;
import java.util.List;

/**
 * A single live telemetry sample exposed by {@code GET /api/vehicles/{id}/live}.
 *
 * <p>This is a projection of {@link TelemetryEvent} with the owner id stripped
 * out — the live buffer is per-vehicle, but we never leak {@code userId} to
 * the API consumer.
 */
public record LiveTelemetryResponse(
        Long readingId,
        Long tripId,
        Long vehicleId,
        Instant recordedAt,
        Double speedKmh,
        Integer rpm,
        Double engineTempC,
        Double fuelLevelPct,
        Double lat,
        Double lng,
        List<String> dtcCodes
) {
}

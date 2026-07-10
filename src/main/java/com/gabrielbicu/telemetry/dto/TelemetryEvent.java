package com.gabrielbicu.telemetry.dto;

import java.time.Instant;

/**
 * Kafka payload emitted for every ingested telemetry reading.
 *
 * <p>Carries just enough to rebuild a "live" view of a vehicle without hitting
 * the database: the vehicle/trip ids, the vehicle owner (so the consumer can
 * enforce ownership on the live model too), and the scalar samples. This is the
 * contract between the ingestion producer and the downstream live-buffer
 * consumer.
 */
public record TelemetryEvent(
        Long readingId,
        Long tripId,
        Long vehicleId,
        Long userId,
        Instant recordedAt,
        Double speedKmh,
        Integer rpm,
        Double engineTempC,
        Double fuelLevelPct,
        java.util.List<String> dtcCodes
) {
}

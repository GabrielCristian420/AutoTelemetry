package com.gabrielbicu.telemetry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * Request body for {@code POST /api/telemetry}, the central ingestion endpoint.
 *
 * <p>A reading is always tied to a trip; the client tells us which trip it's
 * sampling for. {@code dtcCodes} is a list of OBD-II code strings (e.g.
 * {@code ["P0301", "P0420"]}); the service resolves them to {@code DtcCode}
 * entities, creating any unknown ones on the fly so ingestion doesn't fail on
 * a code we haven't catalogued yet.
 *
 * <p>{@code recordedAt} is the device-side timestamp (when the ECU actually
 * produced the sample); distinct from {@code createdAt} on the entity, which
 * is when it lands in our DB. Keeping them separate matters for backfilled /
 * buffered data, the device clock can lag.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelemetryReadingRequest {

    @NotNull
    private Long tripId;

    @NotNull
    private Instant recordedAt;

    private Double speedKmh;
    private Integer rpm;
    private Double engineTempC;
    private Double fuelLevelPct;
    private Double lat;
    private Double lng;

    /** OBD-II code strings; may be empty or null if no fault codes are active. */
    private List<@NotBlank String> dtcCodes;
}

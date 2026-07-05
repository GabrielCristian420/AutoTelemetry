package com.gabrielbicu.telemetry.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for {@code POST /api/trips}.
 *
 * <p>A trip is always started in the context of a vehicle that belongs to the
 * caller, so {@code vehicleId} must be present. {@code startedAt} is optional:
 * if the client omits it, the service will stamp it with {@code Instant.now()}
 * on the server, which is the trustworthy clock for the assistant don't trust
 * the client.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StartTripRequest {

    @NotNull
    private Long vehicleId;

    /** May be null; the service falls back to server time. */
    private java.time.Instant startedAt;
}

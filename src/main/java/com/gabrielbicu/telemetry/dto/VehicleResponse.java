package com.gabrielbicu.telemetry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Response body for vehicle endpoints.
 *
 * <p>Deliberately does not embed the full {@code User}: the API contract should
 * not leak the owner's {@code passwordHash} or email. Only the owning user's id
 * is exposed, which is enough for a client to fetch the user separately if it
 * ever needs to (and which, after week 4, will only resolve to the caller's own
 * id anyway).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleResponse {

    private Long id;
    private Long userId;
    private String vin;
    private String make;
    private String model;
    private Integer year;
    private String plate;
    private Instant createdAt;
}

package com.gabrielbicu.telemetry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for {@code POST /api/vehicles}.
 *
 * <p>The {@code userId} comes from the {@code X-User-Id} header (temporary auth
 * shim until JWT lands in week 4), so it is not part of the request body — the
 * controller reads the header and passes it to the service.
 *
 * <p>Validation here is intentionally minimal; stricter constraints (e.g. a
 * {@code @Pattern} for the VIN format) will be added in week 5.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateVehicleRequest {

    @NotBlank
    @Size(min = 17, max = 17)
    private String vin;

    @NotBlank
    @Size(max = 100)
    private String make;

    @NotBlank
    @Size(max = 100)
    private String model;

    @Size(max = 20)
    private String plate;

    private Integer year;
}

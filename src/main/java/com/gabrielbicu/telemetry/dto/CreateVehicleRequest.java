package com.gabrielbicu.telemetry.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
 * <p>Validation is enforced here with Bean Validation: VIN format via
 * {@code @Pattern} (Week 5), plus length/not-blank rules. The {@code userId}
 * comes from the JWT, not the body.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateVehicleRequest {

    /**
     * VIN: exactly 17 chars, uppercase letters and digits, excluding I/O/Q
     * (those are ambiguous with 1/0). The {@code @Pattern} enforces the
     * standard VIN alphabet; {@code @Size(17)} enforces the length. Both are
     * needed — length alone would accept a 17-char string with illegal chars.
     */
    @NotBlank
    @Size(min = 17, max = 17)
    @Pattern(regexp = "^[A-HJ-NPR-Z0-9]{17}$",
             message = "VIN must be 17 uppercase letters/digits, excluding I, O, Q")
    private String vin;

    @NotBlank
    @Size(max = 100)
    private String make;

    @NotBlank
    @Size(max = 100)
    private String model;

    @Size(max = 20)
    private String plate;

    @Min(1900)
    @Max(2100)
    private Integer year;
}

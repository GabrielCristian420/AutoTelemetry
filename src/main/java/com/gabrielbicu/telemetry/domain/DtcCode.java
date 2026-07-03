package com.gabrielbicu.telemetry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An OBD-II Diagnostic Trouble Code (e.g. {@code P0301} = cylinder 1 misfire).
 *
 * <p>This is a reference table — codes are predefined by the OBD-II standard,
 * not created by users. The many-to-many from {@link TelemetryReading} simply
 * tags which codes were active at a given reading.
 *
 * <p>There is intentionally no back-reference to {@code TelemetryReading}:
 * a code is global reference data, and loading thousands of readings per code
 * would be both useless and expensive. The association is only navigable from
 * the reading side (owning side of the join table).
 */
@Entity
@Table(name = "dtc_codes")
@Getter
@Setter
@NoArgsConstructor
public class DtcCode extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String description;
}

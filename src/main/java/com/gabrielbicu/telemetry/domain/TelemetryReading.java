package com.gabrielbicu.telemetry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * A single sensor reading taken during a trip.
 *
 * <p>This is the high-frequency entity: a single trip can produce thousands of
 * readings (one per second of driving), so the ingestion path is the one that
 * needs to scale.
 */
@Entity
@Table(name = "telemetry_readings")
@Getter
@Setter
@NoArgsConstructor
public class TelemetryReading extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "speed_kmh")
    private Double speedKmh;

    private Integer rpm;

    @Column(name = "engine_temp_c")
    private Double engineTempC;

    @Column(name = "fuel_level_pct")
    private Double fuelLevelPct;

    private Double lat;
    private Double lng;

    /**
     * Active diagnostic trouble codes captured at this reading.
     * Many-to-many via the {@code reading_dtc_codes} join table.
     *
     * <p>Modeled as a {@link Set} rather than a list: a code is either active at
     * a reading or it isn't, so duplicates have no meaning. Safe to use a
     * {@code Set} here because {@link BaseEntity} already defines id-based
     * {@code equals}/{@code hashCode}, which {@code HashSet} relies on.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "reading_dtc_codes",
        joinColumns        = @JoinColumn(name = "reading_id"),
        inverseJoinColumns = @JoinColumn(name = "dtc_code_id")
    )
    private Set<DtcCode> dtcCodes = new HashSet<>();

    /** Convenience method that keeps both sides of the association in sync. */
    public void addDtcCode(DtcCode dtcCode) {
        dtcCodes.add(dtcCode);
        // DtcCode has no back-reference (it is read-only reference data), so
        // there is nothing to update on the other side.
    }
}

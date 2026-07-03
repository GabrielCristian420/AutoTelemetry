package com.gabrielbicu.telemetry.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A single trip made by a vehicle, from start to end.
 * Carries an ordered sequence of telemetry readings.
 */
@Entity
@Table(name = "trips")
@Getter
@Setter
@NoArgsConstructor
public class Trip extends BaseEntity {

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "distance_km")
    private Double distanceKm;

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TelemetryReading> readings = new ArrayList<>();

    public void addReading(TelemetryReading reading) {
        readings.add(reading);
        reading.setTrip(this);
    }
}

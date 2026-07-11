package com.gabrielbicu.telemetry.repository;

import com.gabrielbicu.telemetry.domain.TelemetryReading;
import com.gabrielbicu.telemetry.dto.VehicleStatsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TelemetryReadingRepository extends JpaRepository<TelemetryReading, Long> {

    /**
     * Sensor samples for a trip, in chronological order, as a {@link Page}.
     *
     * <p>A single trip can produce thousands of readings (one per second of
     * driving), so returning a plain {@code List} would blow up memory and
     * latency on the hot path. Callers pass a {@link Pageable} (e.g.
     * {@code ?page=0&size=50}) and get a window back. The total element count is
     * also returned so clients can render pagination controls.
     */
    Page<TelemetryReading> findByTripIdOrderByRecordedAtAsc(Long tripId, Pageable pageable);

    @Query(value = """
            WITH vehicle_readings AS (
                SELECT 
                    r.id,
                    r.speed_kmh,
                    r.rpm,
                    r.fuel_level_pct,
                    r.trip_id,
                    r.recorded_at
                FROM telemetry_readings r
                JOIN trips t ON r.trip_id = t.id
                WHERE t.vehicle_id = :vehicleId
            ),
            fuel_drop_calc AS (
                SELECT 
                    COALESCE(SUM(drop_amount), 0.0) as total_fuel_drop
                FROM (
                    SELECT 
                        fuel_level_pct - LEAD(fuel_level_pct) OVER (PARTITION BY trip_id ORDER BY recorded_at ASC) as drop_amount
                    FROM vehicle_readings
                ) d
                WHERE drop_amount > 0
            ),
            active_dtcs AS (
                SELECT 
                    COUNT(DISTINCT rd.dtc_code_id) as dtc_count
                FROM reading_dtc_codes rd
                JOIN vehicle_readings vr ON rd.reading_id = vr.id
            )
            SELECT 
                ROUND(COALESCE(AVG(vr.speed_kmh), 0.0)::numeric, 2) as avg_speed_kmh,
                COALESCE(MAX(vr.rpm), 0) as max_rpm,
                ROUND(COALESCE((SELECT total_fuel_drop FROM fuel_drop_calc), 0.0)::numeric, 2) as total_fuel_drop_pct,
                COALESCE((SELECT dtc_count FROM active_dtcs), 0) as active_dtc_count
            FROM vehicle_readings vr
            """, nativeQuery = true)
    VehicleStatsProjection findStatsByVehicleId(@Param("vehicleId") Long vehicleId);
}

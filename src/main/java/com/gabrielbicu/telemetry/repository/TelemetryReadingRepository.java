package com.gabrielbicu.telemetry.repository;

import com.gabrielbicu.telemetry.domain.TelemetryReading;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}

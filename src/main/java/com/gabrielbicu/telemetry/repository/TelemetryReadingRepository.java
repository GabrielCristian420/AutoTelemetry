package com.gabrielbicu.telemetry.repository;

import com.gabrielbicu.telemetry.domain.TelemetryReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TelemetryReadingRepository extends JpaRepository<TelemetryReading, Long> {

    /** Sensor samples for a trip, in chronological order. */
    List<TelemetryReading> findByTripIdOrderByRecordedAtAsc(Long tripId);
}

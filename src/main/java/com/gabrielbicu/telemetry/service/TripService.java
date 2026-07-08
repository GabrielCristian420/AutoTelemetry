package com.gabrielbicu.telemetry.service;

import com.gabrielbicu.telemetry.domain.Trip;
import com.gabrielbicu.telemetry.domain.Vehicle;
import com.gabrielbicu.telemetry.dto.StartTripRequest;
import com.gabrielbicu.telemetry.dto.TelemetryReadingResponse;
import com.gabrielbicu.telemetry.dto.TripResponse;
import com.gabrielbicu.telemetry.exception.BusinessRuleException;
import com.gabrielbicu.telemetry.exception.EntityNotFoundException;
import com.gabrielbicu.telemetry.mapper.TelemetryMapper;
import com.gabrielbicu.telemetry.mapper.TripMapper;
import com.gabrielbicu.telemetry.repository.TelemetryReadingRepository;
import com.gabrielbicu.telemetry.repository.TripRepository;
import com.gabrielbicu.telemetry.repository.VehicleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Business logic for {@link Trip}.
 *
 * <p>Trips are tightly coupled to a vehicle, and that vehicle has to belong to
 * the caller. Every method therefore takes a vehicleId (or tripId → load trip
 * → load owning vehicle) and reuses {@link VehicleRepository#findByIdAndUserId}
 * for ownership, exactly the way {@link VehicleService} does. Reusing that one
 * query keeps "the vehicle doesn't exist" and "the vehicle isn't yours" as a
 * single 404.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>{@link #startTrip} creates an open trip (no {@code endedAt}).
 *   <li>{@link #endTrip} closes it; for now it stamps {@code endedAt = now()}
 *       and leaves {@code distanceKm} untouched. Computing distance from the
 *       GPS samples is a Week 5 task (it needs the stats/aggregation layer).
 * </ul>
 */
@Service
public class TripService {

    private final TripRepository tripRepository;
    private final VehicleRepository vehicleRepository;
    private final TelemetryReadingRepository readingRepository;
    private final TripMapper tripMapper;
    private final TelemetryMapper telemetryMapper;

    public TripService(TripRepository tripRepository,
                       VehicleRepository vehicleRepository,
                       TelemetryReadingRepository readingRepository,
                       TripMapper tripMapper,
                       TelemetryMapper telemetryMapper) {
        this.tripRepository = tripRepository;
        this.vehicleRepository = vehicleRepository;
        this.readingRepository = readingRepository;
        this.tripMapper = tripMapper;
        this.telemetryMapper = telemetryMapper;
    }

    @Transactional
    public TripResponse startTrip(StartTripRequest request, Long userId) {
        Vehicle vehicle = vehicleRepository.findByIdAndUserId(request.getVehicleId(), userId)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle", request.getVehicleId()));

        Trip trip = tripMapper.toEntity(request);
        tripMapper.populateVehicle(vehicle, trip);

        // Trust the device clock only if the client sent one. Otherwise stamp
        // the server time — predictable across all clients.
        if (trip.getStartedAt() == null) {
            trip.setStartedAt(Instant.now());
        }

        Trip saved = tripRepository.save(trip);
        return tripMapper.toResponse(saved);
    }

    @Transactional
    public TripResponse endTrip(Long tripId, Long userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("Trip", tripId));

        // Re-check ownership through the parent vehicle.
        Long vehicleId = trip.getVehicle().getId();
        vehicleRepository.findByIdAndUserId(vehicleId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle", vehicleId));

        // Reject ending a trip twice: a second POST /api/trips/{id}/end would
        // otherwise silently overwrite endedAt and corrupt any downstream stats
        // that use (endedAt - startedAt). Mapped to 409 by GlobalExceptionHandler
        // (Week 4); today it bubbles up as a 500.
        if (trip.getEndedAt() != null) {
            throw new BusinessRuleException("Trip " + tripId + " is already ended");
        }

        trip.setEndedAt(Instant.now());
        Trip saved = tripRepository.save(trip);
        return tripMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<TripResponse> listTripsForVehicle(Long vehicleId, Long userId) {
        // Make sure the caller actually owns the vehicle before exposing its trips.
        vehicleRepository.findByIdAndUserId(vehicleId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle", vehicleId));

        return tripRepository.findByVehicleId(vehicleId).stream()
                .map(tripMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<TelemetryReadingResponse> getReadingsForTrip(Long tripId, Pageable pageable, Long userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("Trip", tripId));

        // Ownership check: walk up trip -> vehicle -> user
        Long vehicleId = trip.getVehicle().getId();
        vehicleRepository.findByIdAndUserId(vehicleId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle", vehicleId));

        return readingRepository.findByTripIdOrderByRecordedAtAsc(tripId, pageable)
                .map(telemetryMapper::toResponse);
    }
}

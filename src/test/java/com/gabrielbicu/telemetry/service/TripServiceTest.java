package com.gabrielbicu.telemetry.service;

import com.gabrielbicu.telemetry.domain.Trip;
import com.gabrielbicu.telemetry.domain.Vehicle;
import com.gabrielbicu.telemetry.domain.TelemetryReading;
import com.gabrielbicu.telemetry.dto.TelemetryReadingResponse;
import com.gabrielbicu.telemetry.dto.StartTripRequest;
import com.gabrielbicu.telemetry.dto.TripResponse;
import com.gabrielbicu.telemetry.exception.BusinessRuleException;
import com.gabrielbicu.telemetry.exception.EntityNotFoundException;
import com.gabrielbicu.telemetry.mapper.TelemetryMapper;
import com.gabrielbicu.telemetry.mapper.TripMapper;
import com.gabrielbicu.telemetry.repository.TelemetryReadingRepository;
import com.gabrielbicu.telemetry.repository.TripRepository;
import com.gabrielbicu.telemetry.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TripService}.
 *
 * <p>Pure-Mockito: the {@link TripRepository} and {@link VehicleRepository} are mocked,
 * and we test the business flows of starting, ending, and listing trips.
 */
@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock private TripRepository tripRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private TelemetryReadingRepository readingRepository;
    @Mock private TripMapper tripMapper;
    @Mock private TelemetryMapper telemetryMapper;

    @InjectMocks private TripService tripService;

    @Test
    void startTrip_successfulWithExplicitTimestamp() {
        Long userId = 1L;
        Long vehicleId = 10L;
        Instant startedAt = Instant.now();

        StartTripRequest request = StartTripRequest.builder()
                .vehicleId(vehicleId)
                .startedAt(startedAt)
                .build();

        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);

        Trip mappedTrip = new Trip();
        mappedTrip.setStartedAt(startedAt);

        Trip savedTrip = new Trip();
        savedTrip.setId(100L);
        savedTrip.setStartedAt(startedAt);

        when(vehicleRepository.findByIdAndUserId(vehicleId, userId)).thenReturn(Optional.of(vehicle));
        when(tripMapper.toEntity(request)).thenReturn(mappedTrip);
        when(tripRepository.save(mappedTrip)).thenReturn(savedTrip);
        when(tripMapper.toResponse(savedTrip)).thenReturn(
                TripResponse.builder().id(100L).vehicleId(vehicleId).startedAt(startedAt).build()
        );

        TripResponse response = tripService.startTrip(request, userId);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(startedAt, response.getStartedAt());
        verify(tripMapper).populateVehicle(vehicle, mappedTrip);
        verify(tripRepository).save(mappedTrip);
    }

    @Test
    void startTrip_successfulWithAutoTimestamp() {
        Long userId = 1L;
        Long vehicleId = 10L;

        StartTripRequest request = StartTripRequest.builder()
                .vehicleId(vehicleId)
                .startedAt(null)
                .build();

        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);

        Trip mappedTrip = new Trip(); // startedAt is null initially

        Trip savedTrip = new Trip();
        savedTrip.setId(100L);

        when(vehicleRepository.findByIdAndUserId(vehicleId, userId)).thenReturn(Optional.of(vehicle));
        when(tripMapper.toEntity(request)).thenReturn(mappedTrip);
        when(tripRepository.save(mappedTrip)).thenReturn(savedTrip);
        when(tripMapper.toResponse(savedTrip)).thenReturn(
                TripResponse.builder().id(100L).vehicleId(vehicleId).build()
        );

        TripResponse response = tripService.startTrip(request, userId);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertNotNull(mappedTrip.getStartedAt()); // Auto-stamped by service
        verify(tripRepository).save(mappedTrip);
    }

    @Test
    void startTrip_throwsWhenVehicleNotFoundOrNotOwned() {
        Long userId = 1L;
        Long vehicleId = 99L;
        StartTripRequest request = StartTripRequest.builder().vehicleId(vehicleId).build();

        when(vehicleRepository.findByIdAndUserId(vehicleId, userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> tripService.startTrip(request, userId));
    }

    @Test
    void endTrip_successful() {
        Long userId = 1L;
        Long tripId = 200L;
        Long vehicleId = 10L;

        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);

        Trip trip = new Trip();
        trip.setId(tripId);
        trip.setVehicle(vehicle);
        trip.setStartedAt(Instant.now().minusSeconds(3600));

        Trip savedTrip = new Trip();
        savedTrip.setId(tripId);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(vehicleRepository.findByIdAndUserId(vehicleId, userId)).thenReturn(Optional.of(vehicle));
        when(tripRepository.save(trip)).thenReturn(savedTrip);
        when(tripMapper.toResponse(savedTrip)).thenReturn(TripResponse.builder().id(tripId).build());

        TripResponse response = tripService.endTrip(tripId, userId);

        assertNotNull(response);
        assertNotNull(trip.getEndedAt());
        verify(tripRepository).save(trip);
    }

    @Test
    void endTrip_throwsWhenAlreadyEnded() {
        Long userId = 1L;
        Long tripId = 200L;
        Long vehicleId = 10L;

        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);

        Trip trip = new Trip();
        trip.setId(tripId);
        trip.setVehicle(vehicle);
        trip.setEndedAt(Instant.now().minusSeconds(1000)); // Already ended

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(vehicleRepository.findByIdAndUserId(vehicleId, userId)).thenReturn(Optional.of(vehicle));

        assertThrows(BusinessRuleException.class, () -> tripService.endTrip(tripId, userId));
    }

    @Test
    void endTrip_throwsWhenTripNotFound() {
        Long userId = 1L;
        Long tripId = 999L;

        when(tripRepository.findById(tripId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> tripService.endTrip(tripId, userId));
    }

    @Test
    void listTripsForVehicle_successful() {
        Long userId = 1L;
        Long vehicleId = 10L;

        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);

        Trip trip = new Trip();
        trip.setId(100L);

        when(vehicleRepository.findByIdAndUserId(vehicleId, userId)).thenReturn(Optional.of(vehicle));
        when(tripRepository.findByVehicleId(vehicleId)).thenReturn(List.of(trip));
        when(tripMapper.toResponse(trip)).thenReturn(TripResponse.builder().id(100L).build());

        List<TripResponse> response = tripService.listTripsForVehicle(vehicleId, userId);

        assertNotNull(response);
        assertEquals(1, response.size());
        verify(tripRepository).findByVehicleId(vehicleId);
    }

    @Test
    void listTripsForVehicle_throwsWhenVehicleNotFoundOrNotOwned() {
        Long userId = 1L;
        Long vehicleId = 99L;

        when(vehicleRepository.findByIdAndUserId(vehicleId, userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> tripService.listTripsForVehicle(vehicleId, userId));
    }

    @Test
    void getReadingsForTrip_success() {
        Long userId = 1L;
        Long tripId = 100L;
        Long vehicleId = 10L;

        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);

        Trip trip = new Trip();
        trip.setId(tripId);
        trip.setVehicle(vehicle);

        TelemetryReading reading = new TelemetryReading();
        reading.setId(500L);

        Pageable pageable = Pageable.ofSize(10);
        Page<TelemetryReading> readingPage = new PageImpl<>(List.of(reading), pageable, 1);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(vehicleRepository.findByIdAndUserId(vehicleId, userId)).thenReturn(Optional.of(vehicle));
        when(readingRepository.findByTripIdOrderByRecordedAtAsc(tripId, pageable)).thenReturn(readingPage);
        when(telemetryMapper.toResponse(reading)).thenReturn(TelemetryReadingResponse.builder().id(500L).build());

        Page<TelemetryReadingResponse> response = tripService.getReadingsForTrip(tripId, pageable, userId);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        assertEquals(500L, response.getContent().get(0).getId());
        verify(readingRepository).findByTripIdOrderByRecordedAtAsc(tripId, pageable);
    }

    @Test
    void getReadingsForTrip_throwsWhenTripNotFound() {
        Long userId = 1L;
        Long tripId = 999L;
        Pageable pageable = Pageable.ofSize(10);

        when(tripRepository.findById(tripId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> tripService.getReadingsForTrip(tripId, pageable, userId));
    }

    @Test
    void getReadingsForTrip_throwsWhenVehicleNotOwned() {
        Long userId = 1L;
        Long tripId = 100L;
        Long vehicleId = 10L;

        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);

        Trip trip = new Trip();
        trip.setId(tripId);
        trip.setVehicle(vehicle);

        Pageable pageable = Pageable.ofSize(10);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(vehicleRepository.findByIdAndUserId(vehicleId, userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> tripService.getReadingsForTrip(tripId, pageable, userId));
    }
}

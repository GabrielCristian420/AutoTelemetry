package com.gabrielbicu.telemetry.service;

import com.gabrielbicu.telemetry.domain.DtcCode;
import com.gabrielbicu.telemetry.domain.TelemetryReading;
import com.gabrielbicu.telemetry.domain.Trip;
import com.gabrielbicu.telemetry.domain.Vehicle;
import com.gabrielbicu.telemetry.dto.TelemetryReadingRequest;
import com.gabrielbicu.telemetry.dto.TelemetryReadingResponse;
import com.gabrielbicu.telemetry.exception.EntityNotFoundException;
import com.gabrielbicu.telemetry.mapper.TelemetryMapper;
import com.gabrielbicu.telemetry.repository.DtcCodeRepository;
import com.gabrielbicu.telemetry.repository.TelemetryReadingRepository;
import com.gabrielbicu.telemetry.repository.TripRepository;
import com.gabrielbicu.telemetry.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TelemetryService}.
 */
@ExtendWith(MockitoExtension.class)
class TelemetryServiceTest {

    @Mock private TelemetryReadingRepository readingRepository;
    @Mock private TripRepository tripRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private DtcCodeRepository dtcCodeRepository;
    @Mock private TelemetryMapper telemetryMapper;
    @Mock private DtcDecoderService dtcDecoderService;
    @Mock private TelemetryEventProducer eventProducer;

    @InjectMocks private TelemetryService telemetryService;

    @Test
    void ingestReading_success() {
        Long userId = 1L;
        Long tripId = 10L;
        Long vehicleId = 5L;

        Trip trip = new Trip();
        trip.setId(tripId);
        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);
        trip.setVehicle(vehicle);

        TelemetryReadingRequest request = TelemetryReadingRequest.builder()
                .tripId(tripId)
                .recordedAt(Instant.now())
                .speedKmh(100.0)
                .rpm(2500)
                .fuelLevelPct(50.0)
                .dtcCodes(List.of())
                .build();

        TelemetryReading mapped = new TelemetryReading();
        mapped.setDtcCodes(new HashSet<>());
        TelemetryReading saved = new TelemetryReading();
        saved.setId(100L);
        saved.setDtcCodes(new HashSet<>());

        TelemetryReadingResponse expectedResponse = TelemetryReadingResponse.builder()
                .id(100L)
                .speedKmh(100.0)
                .rpm(2500)
                .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(vehicleRepository.findByIdAndUserId(vehicleId, userId)).thenReturn(Optional.of(vehicle));
        when(telemetryMapper.toEntity(request)).thenReturn(mapped);
        when(readingRepository.save(mapped)).thenReturn(saved);
        when(telemetryMapper.toResponse(saved)).thenReturn(expectedResponse);

        TelemetryReadingResponse result = telemetryService.ingestReading(request, userId);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        verify(telemetryMapper).populateTrip(trip, mapped);
        verify(readingRepository).save(mapped);
        verify(eventProducer).publishReading(saved, vehicleId, userId);
    }

    @Test
    void ingestReading_tripNotFound_throwsEntityNotFoundException() {
        Long userId = 1L;
        Long tripId = 999L;

        TelemetryReadingRequest request = TelemetryReadingRequest.builder()
                .tripId(tripId)
                .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> telemetryService.ingestReading(request, userId));
        verify(readingRepository, never()).save(any());
        verify(eventProducer, never()).publishReading(any(), any(), any());
    }

    @Test
    void ingestReading_vehicleNotOwned_throwsEntityNotFoundException() {
        Long userId = 1L;
        Long tripId = 10L;
        Long vehicleId = 5L;

        Trip trip = new Trip();
        trip.setId(tripId);
        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);
        trip.setVehicle(vehicle);

        TelemetryReadingRequest request = TelemetryReadingRequest.builder()
                .tripId(tripId)
                .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(vehicleRepository.findByIdAndUserId(vehicleId, userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> telemetryService.ingestReading(request, userId));
        verify(readingRepository, never()).save(any());
        verify(eventProducer, never()).publishReading(any(), any(), any());
    }

    @Test
    void ingestReading_withEmptyDtcCodes() {
        Long userId = 1L;
        Long tripId = 10L;
        Long vehicleId = 5L;

        Trip trip = new Trip();
        trip.setId(tripId);
        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);
        trip.setVehicle(vehicle);

        TelemetryReadingRequest request = TelemetryReadingRequest.builder()
                .tripId(tripId)
                .dtcCodes(null)
                .build();

        TelemetryReading mapped = new TelemetryReading();
        mapped.setDtcCodes(new HashSet<>());
        TelemetryReading saved = new TelemetryReading();
        saved.setDtcCodes(new HashSet<>());

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(vehicleRepository.findByIdAndUserId(vehicleId, userId)).thenReturn(Optional.of(vehicle));
        when(telemetryMapper.toEntity(request)).thenReturn(mapped);
        when(readingRepository.save(mapped)).thenReturn(saved);

        telemetryService.ingestReading(request, userId);

        verify(dtcCodeRepository, never()).findByCode(any());
        verify(dtcCodeRepository, never()).save(any());
    }

    @Test
    void ingestReading_withNewDtcCode_resolvesAndPersists() {
        Long userId = 1L;
        Long tripId = 10L;
        Long vehicleId = 5L;

        Trip trip = new Trip();
        trip.setId(tripId);
        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);
        trip.setVehicle(vehicle);

        TelemetryReadingRequest request = TelemetryReadingRequest.builder()
                .tripId(tripId)
                .dtcCodes(List.of("P0301"))
                .build();

        TelemetryReading mapped = new TelemetryReading();
        mapped.setDtcCodes(new HashSet<>());
        TelemetryReading saved = new TelemetryReading();
        saved.setDtcCodes(new HashSet<>());

        DtcCode newDtc = new DtcCode();
        newDtc.setCode("P0301");
        newDtc.setDescription("Cylinder 1 Misfire Detected");

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(vehicleRepository.findByIdAndUserId(vehicleId, userId)).thenReturn(Optional.of(vehicle));
        when(telemetryMapper.toEntity(request)).thenReturn(mapped);
        when(dtcCodeRepository.findByCode("P0301")).thenReturn(Optional.empty());
        when(dtcDecoderService.decode("P0301")).thenReturn(Optional.of("Cylinder 1 Misfire Detected"));
        when(dtcCodeRepository.save(any(DtcCode.class))).thenReturn(newDtc);
        when(readingRepository.save(mapped)).thenReturn(saved);

        telemetryService.ingestReading(request, userId);

        assertTrue(mapped.getDtcCodes().contains(newDtc));
        verify(dtcCodeRepository).save(any(DtcCode.class));
    }

    @Test
    void ingestReading_withExistingDtcCode_reusesCode() {
        Long userId = 1L;
        Long tripId = 10L;
        Long vehicleId = 5L;

        Trip trip = new Trip();
        trip.setId(tripId);
        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);
        trip.setVehicle(vehicle);

        TelemetryReadingRequest request = TelemetryReadingRequest.builder()
                .tripId(tripId)
                .dtcCodes(List.of("P0420"))
                .build();

        TelemetryReading mapped = new TelemetryReading();
        mapped.setDtcCodes(new HashSet<>());
        TelemetryReading saved = new TelemetryReading();
        saved.setDtcCodes(new HashSet<>());

        DtcCode existingDtc = new DtcCode();
        existingDtc.setCode("P0420");
        existingDtc.setDescription("Catalyst System Efficiency Below Threshold");

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(vehicleRepository.findByIdAndUserId(vehicleId, userId)).thenReturn(Optional.of(vehicle));
        when(telemetryMapper.toEntity(request)).thenReturn(mapped);
        when(dtcCodeRepository.findByCode("P0420")).thenReturn(Optional.of(existingDtc));
        when(readingRepository.save(mapped)).thenReturn(saved);

        telemetryService.ingestReading(request, userId);

        assertTrue(mapped.getDtcCodes().contains(existingDtc));
        verify(dtcDecoderService, never()).decode(any());
        verify(dtcCodeRepository, never()).save(any(DtcCode.class));
    }

    @Test
    void ingestReading_eventProducerThrows_doesNotFailIngestion() {
        Long userId = 1L;
        Long tripId = 10L;
        Long vehicleId = 5L;

        Trip trip = new Trip();
        trip.setId(tripId);
        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);
        trip.setVehicle(vehicle);

        TelemetryReadingRequest request = TelemetryReadingRequest.builder()
                .tripId(tripId)
                .dtcCodes(List.of())
                .build();

        TelemetryReading mapped = new TelemetryReading();
        mapped.setDtcCodes(new HashSet<>());
        TelemetryReading saved = new TelemetryReading();
        saved.setDtcCodes(new HashSet<>());

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(vehicleRepository.findByIdAndUserId(vehicleId, userId)).thenReturn(Optional.of(vehicle));
        when(telemetryMapper.toEntity(request)).thenReturn(mapped);
        when(readingRepository.save(mapped)).thenReturn(saved);
        
        // Mock eventProducer throwing an error when publishing.
        doThrow(new RuntimeException("Kafka is down")).when(eventProducer).publishReading(saved, vehicleId, userId);

        // This call should complete successfully without propagating the Kafka error.
        telemetryService.ingestReading(request, userId);

        verify(readingRepository).save(mapped);
        verify(eventProducer).publishReading(saved, vehicleId, userId);
    }
}

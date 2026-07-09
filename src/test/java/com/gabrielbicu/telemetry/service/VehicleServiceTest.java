package com.gabrielbicu.telemetry.service;

import com.gabrielbicu.telemetry.domain.User;
import com.gabrielbicu.telemetry.domain.Vehicle;
import com.gabrielbicu.telemetry.dto.CreateVehicleRequest;
import com.gabrielbicu.telemetry.dto.VehicleResponse;
import com.gabrielbicu.telemetry.dto.VehicleStatsProjection;
import com.gabrielbicu.telemetry.dto.VehicleStatsResponse;
import com.gabrielbicu.telemetry.exception.EntityNotFoundException;
import com.gabrielbicu.telemetry.mapper.VehicleMapper;
import com.gabrielbicu.telemetry.repository.TelemetryReadingRepository;
import com.gabrielbicu.telemetry.repository.UserRepository;
import com.gabrielbicu.telemetry.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VehicleService}.
 *
 * <p>Pure-Mockito: the {@link com.gabrielbicu.telemetry.repository.VehicleRepository}
 * and {@link UserRepository} are mocked, so this never touches a database.
 * The {@link VehicleMapper} is also mocked because MapStruct-generated impls
 * are exercised separately by integration tests; here we want to assert the
 * service's orchestration, not the mapper's field-by-field wiring.
 */
@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock private VehicleRepository vehicleRepository;
    @Mock private UserRepository userRepository;
    @Mock private TelemetryReadingRepository readingRepository;
    @Mock private VehicleMapper vehicleMapper;

    @InjectMocks private VehicleService vehicleService;

    @Test
    void createVehicle_persistsAndReturnsResponseForExistingUser() {
        Long userId = 7L;
        User owner = new User();
        owner.setId(userId);

        CreateVehicleRequest request = CreateVehicleRequest.builder()
                .vin("WBA7E2C50KG876543")
                .make("BMW")
                .model("330i")
                .build();
        Vehicle mapped = new Vehicle();
        Vehicle saved = new Vehicle();
        saved.setId(42L);

        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));
        when(vehicleMapper.toEntity(request)).thenReturn(mapped);
        when(vehicleRepository.save(mapped)).thenReturn(saved);
        VehicleResponse expectedResponse = VehicleResponse.builder().id(42L).userId(userId).build();
        when(vehicleMapper.toResponse(saved)).thenReturn(expectedResponse);

        VehicleResponse result = vehicleService.createVehicle(request, userId);

        assertEquals(42L, result.getId());
        // The owning user must actually be stamped before save — not just left null.
        verify(vehicleMapper).populateUser(owner, mapped);
        verify(vehicleRepository).save(mapped);
    }

    @Test
    void createVehicle_throwsWhenUserDoesNotExist() {
        Long userId = 99L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> vehicleService.createVehicle(CreateVehicleRequest.builder().vin("1").build(), userId));
    }

    @Test
    void getVehicle_throwsWhenVehicleBelongsToAnotherUser() {
        // findByIdAndUserId returns empty whether the row is missing or just not the caller's
        // — that's the whole point, both flow into the same 404.
        when(vehicleRepository.findByIdAndUserId(1L, 5L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> vehicleService.getVehicle(1L, 5L));
    }

    @Test
    void listVehicles_delegatesToFindByUserId() {
        Long userId = 5L;
        Vehicle v = new Vehicle();
        v.setId(8L);
        when(vehicleRepository.findByUserId(userId)).thenReturn(List.of(v));
        when(vehicleMapper.toResponse(v))
                .thenReturn(VehicleResponse.builder().id(8L).userId(userId).build());

        List<VehicleResponse> result = vehicleService.listVehicles(userId);

        assertEquals(1, result.size());
        verify(vehicleRepository).findByUserId(userId);
    }

    @Test
    void deleteVehicle_throwsWhenVehicleDoesNotBelongToCaller() {
        when(vehicleRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> vehicleService.deleteVehicle(1L, 2L));
    }

    @Test
    void getVehicleStats_success() {
        Long vehicleId = 42L;
        Long userId = 7L;
        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);

        VehicleStatsProjection projection = new VehicleStatsProjection() {
            @Override public Double getAvgSpeedKmh() { return 65.5; }
            @Override public Integer getMaxRpm() { return 3500; }
            @Override public Double getTotalFuelDropPct() { return 12.3; }
            @Override public Long getActiveDtcCount() { return 2L; }
        };

        when(vehicleRepository.findByIdAndUserId(vehicleId, userId)).thenReturn(Optional.of(vehicle));
        when(readingRepository.findStatsByVehicleId(vehicleId)).thenReturn(projection);

        VehicleStatsResponse response = vehicleService.getVehicleStats(vehicleId, userId);

        assertEquals(65.5, response.getAvgSpeedKmh());
        assertEquals(3500, response.getMaxRpm());
        assertEquals(12.3, response.getTotalFuelDropPct());
        assertEquals(2L, response.getActiveDtcCount());
    }

    @Test
    void getVehicleStats_throwsWhenVehicleNotFoundOrNotOwned() {
        Long vehicleId = 42L;
        Long userId = 7L;

        when(vehicleRepository.findByIdAndUserId(vehicleId, userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> vehicleService.getVehicleStats(vehicleId, userId));
    }
}

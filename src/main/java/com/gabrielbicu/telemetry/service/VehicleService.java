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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for {@link Vehicle}.
 *
 * <p>Every method takes a {@code userId} so that operations are scoped to the
 * caller. This is the (temporary) Week 3 stand-in for real authentication: the
 * id comes from the {@code X-User-Id} request header. Once JWT lands in Week 4,
 * the controller will extract the id from the token instead of a header — the
 * service contract stays the same.
 *
 * <p>Ownership is enforced <em>in the query</em> where possible, using
 * {@link com.gabrielbicu.telemetry.repository.VehicleRepository#findByIdAndUserId}.
 * That method returns an empty {@link java.util.Optional} when the row exists but
 * belongs to someone else, so "not found" and "not yours" collapse into the same
 * 404 — there's no information leak about which vehicle ids exist.
 */
@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final TelemetryReadingRepository readingRepository;
    private final VehicleMapper vehicleMapper;

    public VehicleService(VehicleRepository vehicleRepository,
                          UserRepository userRepository,
                          TelemetryReadingRepository readingRepository,
                          VehicleMapper vehicleMapper) {
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
        this.readingRepository = readingRepository;
        this.vehicleMapper = vehicleMapper;
    }

    @Transactional
    public VehicleResponse createVehicle(CreateVehicleRequest request, Long userId) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User", userId));
        Vehicle vehicle = vehicleMapper.toEntity(request);
        vehicle.setVin(request.getVin().toUpperCase());
        vehicleMapper.populateUser(owner, vehicle);
        Vehicle saved = vehicleRepository.save(vehicle);
        return vehicleMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> listVehicles(Long userId) {
        return vehicleRepository.findByUserId(userId).stream()
                .map(vehicleMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public VehicleResponse getVehicle(Long id, Long userId) {
        return vehicleRepository.findByIdAndUserId(id, userId)
                .map(vehicleMapper::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle", id));
    }

    @Transactional
    public void deleteVehicle(Long id, Long userId) {
        Vehicle vehicle = vehicleRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle", id));
        vehicleRepository.delete(vehicle);
    }

    /**
     * Lightweight ownership assertion used by endpoints that don't need the
     * full vehicle payload (e.g. the live buffer view). Throws
     * {@link EntityNotFoundException} if the vehicle doesn't exist or isn't
     * owned by the caller — same 404 semantics as the other methods.
     */
    @Transactional(readOnly = true)
    public void requireOwnership(Long vehicleId, Long userId) {
        vehicleRepository.findByIdAndUserId(vehicleId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle", vehicleId));
    }

    @Transactional(readOnly = true)
    public VehicleStatsResponse getVehicleStats(Long vehicleId, Long userId) {
        vehicleRepository.findByIdAndUserId(vehicleId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle", vehicleId));

        VehicleStatsProjection projection = readingRepository.findStatsByVehicleId(vehicleId);
        if (projection == null) {
            // No readings for this vehicle yet — return a zeroed-out stats object
            // instead of risking an NPE on the projection access below.
            return VehicleStatsResponse.builder()
                    .avgSpeedKmh(0.0)
                    .maxRpm(0)
                    .totalFuelDropPct(0.0)
                    .activeDtcCount(0L)
                    .build();
        }

        return VehicleStatsResponse.builder()
                .avgSpeedKmh(projection.getAvgSpeedKmh() != null ? projection.getAvgSpeedKmh() : 0.0)
                .maxRpm(projection.getMaxRpm() != null ? projection.getMaxRpm() : 0)
                .totalFuelDropPct(projection.getTotalFuelDropPct() != null ? projection.getTotalFuelDropPct() : 0.0)
                .activeDtcCount(projection.getActiveDtcCount() != null ? projection.getActiveDtcCount() : 0L)
                .build();
    }
}

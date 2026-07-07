package com.gabrielbicu.telemetry.controller;

import com.gabrielbicu.telemetry.dto.CreateVehicleRequest;
import com.gabrielbicu.telemetry.dto.TripResponse;
import com.gabrielbicu.telemetry.dto.VehicleResponse;
import com.gabrielbicu.telemetry.service.TripService;
import com.gabrielbicu.telemetry.service.VehicleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * REST endpoints for vehicles.
 *
 * <p>Caller identity comes from {@link AuthenticationPrincipal}, resolved by
 * Spring Security from the JWT in the Authorization header (see
 * {@link com.gabrielbicu.telemetry.config.JwtAuthFilter}). Because every route
 * here is {@code authenticated()} in SecurityConfig, the principal is
 * guaranteed non-null when these methods run — Spring returns 401 before
 * reaching the controller if the caller isn't authenticated.
 */
@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;
    private final TripService tripService;

    public VehicleController(VehicleService vehicleService, TripService tripService) {
        this.vehicleService = vehicleService;
        this.tripService = tripService;
    }

    @PostMapping
    public ResponseEntity<VehicleResponse> createVehicle(
            @Valid @RequestBody CreateVehicleRequest request,
            @AuthenticationPrincipal Long userId) {
        VehicleResponse saved = vehicleService.createVehicle(request, userId);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saved.getId())
                .toUri();
        return ResponseEntity.created(location).body(saved);
    }

    @GetMapping
    public List<VehicleResponse> listVehicles(@AuthenticationPrincipal Long userId) {
        return vehicleService.listVehicles(userId);
    }

    @GetMapping("/{id}")
    public VehicleResponse getVehicle(@PathVariable Long id,
                                       @AuthenticationPrincipal Long userId) {
        return vehicleService.getVehicle(id, userId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVehicle(@PathVariable Long id,
                                              @AuthenticationPrincipal Long userId) {
        vehicleService.deleteVehicle(id, userId);
        return ResponseEntity.noContent().build();
    }

    /** Sub-resource: trips for a vehicle. Ownership is re-checked inside the service. */
    @GetMapping("/{id}/trips")
    public List<TripResponse> listTripsForVehicle(@PathVariable Long id,
                                                  @AuthenticationPrincipal Long userId) {
        return tripService.listTripsForVehicle(id, userId);
    }
}

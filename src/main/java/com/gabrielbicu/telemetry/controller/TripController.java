package com.gabrielbicu.telemetry.controller;

import com.gabrielbicu.telemetry.dto.StartTripRequest;
import com.gabrielbicu.telemetry.dto.TripResponse;
import com.gabrielbicu.telemetry.service.TripService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * REST endpoints for trips.
 *
 * <p>Trips are nested under a vehicle in the URL space for sub-resources
 * ({@code GET /api/vehicles/{id}/trips}), but the start trip action lives at
 * the top level ({@code POST /api/trips}) so the request body carries the
 * {@code vehicleId} — keeping the collection resource uniform.
 *
 * <p>The caller's id is resolved by Spring Security from the JWT (see
 * {@link com.gabrielbicu.telemetry.config.JwtAuthFilter}); SecurityConfig
 * rejects unauthenticated requests with 401 before they reach this class.
 */
@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping
    public ResponseEntity<TripResponse> startTrip(
            @Valid @RequestBody StartTripRequest request,
            @AuthenticationPrincipal Long userId) {
        TripResponse saved = tripService.startTrip(request, userId);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saved.getId())
                .toUri();
        return ResponseEntity.created(location).body(saved);
    }

    @PostMapping("/{id}/end")
    public TripResponse endTrip(@PathVariable Long id,
                                 @AuthenticationPrincipal Long userId) {
        return tripService.endTrip(id, userId);
    }
}

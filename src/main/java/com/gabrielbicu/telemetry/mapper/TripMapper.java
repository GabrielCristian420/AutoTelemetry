package com.gabrielbicu.telemetry.mapper;

import com.gabrielbicu.telemetry.domain.Trip;
import com.gabrielbicu.telemetry.domain.Vehicle;
import com.gabrielbicu.telemetry.dto.StartTripRequest;
import com.gabrielbicu.telemetry.dto.TripResponse;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Maps between the {@link Trip} entity and its DTOs.
 *
 * <p>Same pattern as {@link VehicleMapper}: the owning parent ({@link Vehicle})
 * is set by the service, not by the mapper, because the request only carries
 * the parent's id — the service resolves the actual entity (with ownership
 * checks) before stamping it on the trip.
 */
@Mapper(componentModel = "spring")
public interface TripMapper {

    @Mapping(target = "id",         ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "vehicle",   ignore = true)
    @Mapping(target = "readings",  ignore = true)
    // A freshly-started trip is open: endedAt/distanceKm are null until
    // POST /api/trips/{id}/end is called. Mapped explicitly to silence the
    // MapStruct "unmapped target property" warning and document the intent.
    @Mapping(target = "endedAt",    ignore = true)
    @Mapping(target = "distanceKm", ignore = true)
    Trip toEntity(StartTripRequest request);

    @Mapping(target = "vehicleId", source = "vehicle.id")
    TripResponse toResponse(Trip trip);

    @AfterMapping
    default void populateVehicle(Vehicle vehicle, @MappingTarget Trip trip) {
        trip.setVehicle(vehicle);
    }
}

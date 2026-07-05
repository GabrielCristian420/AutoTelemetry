package com.gabrielbicu.telemetry.mapper;

import com.gabrielbicu.telemetry.domain.User;
import com.gabrielbicu.telemetry.domain.Vehicle;
import com.gabrielbicu.telemetry.dto.CreateVehicleRequest;
import com.gabrielbicu.telemetry.dto.VehicleResponse;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Maps between the {@link Vehicle} entity and its DTOs.
 *
 * <p>Two notable decisions:
 * <ul>
 *   <li>{@code componentModel = "spring"} makes the generated impl a Spring bean,
 *       so it can be {@code @Autowired} into services instead of using a static
 *       {@code INSTANCE}.
 *   <li>{@code userId} is mapped explicitly from {@code user.getId()}. The owning
 *       {@link User} is a LAZY-loaded {@code @ManyToOne}, but we only reach its id,
 *       which Hibernate already carries on the proxy without issuing a SELECT —
 *       so building a {@link VehicleResponse} for a list of vehicles does not
 *       trigger N+1.
 * </ul>
 *
 * <p>{@link CreateVehicleRequest} carries only the writable fields; the owner is
 * injected by the service, not by the mapper. {@link #populateUser(User, Vehicle)}
 * stamps it on the entity after mapping the request fields.
 */
@Mapper(componentModel = "spring")
public interface VehicleMapper {

    @Mapping(target = "id",        ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "user",      ignore = true)
    @Mapping(target = "trips",     ignore = true)
    Vehicle toEntity(CreateVehicleRequest request);

    @Mapping(target = "userId", source = "user.id")
    VehicleResponse toResponse(Vehicle vehicle);

    /**
     * Stamps the owning user onto a freshly-mapped entity. Called by the service
     * after {@link #toEntity(CreateVehicleRequest)}, because the user is resolved
     * from the request header ({@code X-User-Id}), not from the request body.
     */
    @AfterMapping
    default void populateUser(User user, @MappingTarget Vehicle vehicle) {
        vehicle.setUser(user);
    }
}

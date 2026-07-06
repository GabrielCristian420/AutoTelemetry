package com.gabrielbicu.telemetry.mapper;

import com.gabrielbicu.telemetry.domain.DtcCode;
import com.gabrielbicu.telemetry.domain.TelemetryReading;
import com.gabrielbicu.telemetry.domain.Trip;
import com.gabrielbicu.telemetry.dto.TelemetryReadingRequest;
import com.gabrielbicu.telemetry.dto.TelemetryReadingResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps between the {@link TelemetryReading} entity and its DTOs.
 *
 * <p>Two special cases:
 * <ol>
 *   <li><b>Entity → response:</b> the entity holds a {@code Set<DtcCode>}, the
 *       response carries the codes as flat strings. The {@link #dtcCodesToStrings}
 *       helper extracts just the {@link DtcCode#getCode()} values so the API
 *       contract never leaks the internal id/createdAt of reference rows.
 *   <li><b>Request → entity:</b> the reverse direction cannot be a plain
 *       MapStruct method because {@code List<String>} must be resolved against
 *       the {@code dtc_codes} table (creating unknown codes on the fly). That
 *       lookup is business logic and lives in {@code TelemetryService}, not here.
 *       This mapper only handles the part of the request that's pure mapping:
 *       scalar fields plus {@code recordedAt}, {@code speedKmh}, {@code rpm}, etc.
 * </ol>
 *
 * <p>{@code trip} is set by the service (the same parent-ownership pattern as
 * the other mappers); {@code dtcCodes} is filled by the service after resolving
 * the code strings.
 */
@Mapper(componentModel = "spring")
public interface TelemetryMapper {

    @Mapping(target = "id",         ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "trip",      ignore = true)
    @Mapping(target = "dtcCodes", ignore = true)
    TelemetryReading toEntity(TelemetryReadingRequest request);

    @Mapping(target = "tripId",    source = "trip.id")
    @Mapping(target = "dtcCodes", source = "dtcCodes", qualifiedByName = "dtcCodesToStrings")
    TelemetryReadingResponse toResponse(TelemetryReading reading);

    /**
     * Stamps the owning trip onto a freshly-mapped reading. Called manually by
     * the service after {@link #toEntity(TelemetryReadingRequest)}: the trip is
     * resolved (with ownership checks) inside the service, not through
     * {@code @AfterMapping} which would need {@code Trip} as an extra parameter
     * of {@code toEntity}. See {@link com.gabrielbicu.telemetry.mapper.VehicleMapper#populateUser}
     * for the same pattern.
     */
    default void populateTrip(Trip trip, TelemetryReading reading) {
        reading.setTrip(trip);
    }

    /** Extracts just the OBD-II code strings from the reference entities. */
    @Named("dtcCodesToStrings")
    default List<String> dtcCodesToStrings(Set<DtcCode> codes) {
        if (codes == null) {
            return List.of();
        }
        return codes.stream().map(DtcCode::getCode).sorted().collect(Collectors.toList());
    }
}

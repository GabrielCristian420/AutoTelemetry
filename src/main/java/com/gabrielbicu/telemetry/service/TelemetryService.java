package com.gabrielbicu.telemetry.service;

import com.gabrielbicu.telemetry.domain.DtcCode;
import com.gabrielbicu.telemetry.domain.TelemetryReading;
import com.gabrielbicu.telemetry.domain.Trip;
import com.gabrielbicu.telemetry.dto.TelemetryReadingRequest;
import com.gabrielbicu.telemetry.dto.TelemetryReadingResponse;
import com.gabrielbicu.telemetry.exception.EntityNotFoundException;
import com.gabrielbicu.telemetry.mapper.TelemetryMapper;
import com.gabrielbicu.telemetry.repository.DtcCodeRepository;
import com.gabrielbicu.telemetry.repository.TelemetryReadingRepository;
import com.gabrielbicu.telemetry.repository.TripRepository;
import com.gabrielbicu.telemetry.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business logic for {@link TelemetryReading} — the central ingestion path.
 *
 * <p>The interesting part is {@link #resolveDtcCodes}. The request carries DTC
 * codes as flat strings (e.g. {@code "P0301"}); the entity holds a
 * {@code Set<DtcCode>}. Two things have to happen per code:
 * <ol>
 *   <li>Look it up in {@code dtc_codes}. Most codes (P0xxx) are catalogued.
 *   <li>If unknown, create a new {@link DtcCode} with the raw string and a
 *       placeholder description. <b>Why:</b> ingestion shouldn't fail just
 *       because a producer sends an OBD-II code the catalog doesn't list yet;
 *       the future "DTC decoder" stretch goal can backfill the description.
 * </ol>
 *
 * <p>Ownership is enforced by walking up trip → vehicle → user, using the same
 * {@link VehicleRepository#findByIdAndUserId} query. One SELECT per level, no
 * N+1: we only need the parent ids.
 */
@Service
public class TelemetryService {

    private final TelemetryReadingRepository readingRepository;
    private final TripRepository tripRepository;
    private final VehicleRepository vehicleRepository;
    private final DtcCodeRepository dtcCodeRepository;
    private final TelemetryMapper telemetryMapper;

    public TelemetryService(TelemetryReadingRepository readingRepository,
                            TripRepository tripRepository,
                            VehicleRepository vehicleRepository,
                            DtcCodeRepository dtcCodeRepository,
                            TelemetryMapper telemetryMapper) {
        this.readingRepository = readingRepository;
        this.tripRepository = tripRepository;
        this.vehicleRepository = vehicleRepository;
        this.dtcCodeRepository = dtcCodeRepository;
        this.telemetryMapper = telemetryMapper;
    }

    @Transactional
    public TelemetryReadingResponse ingestReading(TelemetryReadingRequest request, Long userId) {
        Trip trip = tripRepository.findById(request.getTripId())
                .orElseThrow(() -> new EntityNotFoundException("Trip", request.getTripId()));

        // Ownership check: walk up trip -> vehicle -> user.
        Long vehicleId = trip.getVehicle().getId();
        vehicleRepository.findByIdAndUserId(vehicleId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle", vehicleId));

        TelemetryReading reading = telemetryMapper.toEntity(request);
        telemetryMapper.populateTrip(trip, reading);

        Set<DtcCode> codes = resolveDtcCodes(request.getDtcCodes());
        reading.getDtcCodes().addAll(codes);

        TelemetryReading saved = readingRepository.save(reading);
        return telemetryMapper.toResponse(saved);
    }

    /**
     * Maps incoming code strings to {@link DtcCode} entities, creating any
     * unknown ones in the process. Returns an empty set for a null/empty list
     * (no fault codes active at this reading).
     */
    private Set<DtcCode> resolveDtcCodes(List<String> codeStrings) {
        if (codeStrings == null || codeStrings.isEmpty()) {
            return new HashSet<>();
        }

        // De-duplicate the incoming list (codes are unique per reading).
        Set<String> distinctCodes = codeStrings.stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());

        Set<DtcCode> resolved = new HashSet<>();
        for (String code : distinctCodes) {
            DtcCode dtc = dtcCodeRepository.findByCode(code)
                    .orElseGet(() -> persistUnknownCode(code));
            resolved.add(dtc);
        }
        return resolved;
    }

    private DtcCode persistUnknownCode(String code) {
        DtcCode newCode = new DtcCode();
        newCode.setCode(code);
        newCode.setDescription("Unknown OBD-II code (auto-created on ingestion)");
        return dtcCodeRepository.save(newCode);
    }
}

package com.gabrielbicu.telemetry.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Translates raw OBD-II diagnostic trouble codes (e.g. {@code P0301}) into
 * human-readable descriptions.
 *
 * <p>OBD-II codes are structured: the first character is the system
 * ({@code P}=powertrain, {@code C}=chassis, {@code B}=body, {@code U}=network/user),
 * the second is the digit/letter that distinguishes standard ({@code 0}) vs
 * manufacturer-specific ({@code 1}/{@code 2}/{@code 3}) codes, and the last
 * three identify the fault. We keep a small lookup of the most common
 * generic (P0xxx) codes and fall back to a system-level description for the
 * rest — far better than the previous "Unknown OBD-II code" placeholder.
 *
 * <p>This is intentionally a pure, side-effect-free service: it never touches
 * the database, so it is trivial to unit test and can be reused by the
 * ingestion path (to backfill unknown codes) and by any analytics consumer.
 */
@Service
public class DtcDecoderService {

    /** Well-known generic powertrain (P0xxx) codes. */
    private static final Map<String, String> GENERIC_PO_codes = Map.ofEntries(
            Map.entry("P0101", "Mass Air Flow (MAF) Circuit Range/Performance"),
            Map.entry("P0102", "MAF Circuit Low Input"),
            Map.entry("P0103", "MAF Circuit High Input"),
            Map.entry("P0113", "Intake Air Temperature (IAT) Circuit High Input"),
            Map.entry("P0128", "Coolant Thermostat Below Regulating Temperature"),
            Map.entry("P0171", "System Too Lean (Bank 1)"),
            Map.entry("P0172", "System Too Rich (Bank 1)"),
            Map.entry("P0300", "Random/Multiple Cylinder Misfire Detected"),
            Map.entry("P0301", "Cylinder 1 Misfire Detected"),
            Map.entry("P0302", "Cylinder 2 Misfire Detected"),
            Map.entry("P0303", "Cylinder 3 Misfire Detected"),
            Map.entry("P0304", "Cylinder 4 Misfire Detected"),
            Map.entry("P0420", "Catalyst System Efficiency Below Threshold (Bank 1)"),
            Map.entry("P0430", "Catalyst System Efficiency Below Threshold (Bank 2)"),
            Map.entry("P0442", "EVAP System Small Leak Detected"),
            Map.entry("P0500", "Vehicle Speed Sensor Malfunction"),
            Map.entry("P0562", "System Voltage Low"),
            Map.entry("P0606", "Powertrain Control Module (PCM) Processor Fault"),
            Map.entry("P0700", "Transmission Control System Malfunction")
    );

    private static final Map<Character, String> SYSTEM_DESCRIPTIONS = Map.of(
            'P', "Powertrain fault",
            'C', "Chassis fault",
            'B', "Body fault",
            'U', "Network / communication fault"
    );

    /**
     * Returns a human-readable description for the given OBD-II code.
     * Returns {@link Optional#empty()} for malformed codes (so the caller can
     * decide whether to keep the raw code or flag it).
     */
    public Optional<String> decode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase();
        if (!normalized.matches("^[PCBU][0-3A-C][0-9A-F]{3}$")) {
            return Optional.empty();
        }

        // Exact match on a known generic code wins.
        if (GENERIC_PO_codes.containsKey(normalized)) {
            return Optional.of(GENERIC_PO_codes.get(normalized));
        }

        // Otherwise describe at the system level (P0xxx/P1xxx/B0xxx/...).
        char system = normalized.charAt(0);
        String base = SYSTEM_DESCRIPTIONS.getOrDefault(system, "Unknown OBD-II fault");
        String scope = normalized.charAt(1) == '0' ? "generic" : "manufacturer-specific";
        return Optional.of(base + " (" + scope + " code " + normalized + ")");
    }
}

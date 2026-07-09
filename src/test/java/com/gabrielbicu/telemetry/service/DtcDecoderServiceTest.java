package com.gabrielbicu.telemetry.service;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DtcDecoderService} — pure logic, no Spring context.
 */
class DtcDecoderServiceTest {

    private final DtcDecoderService decoder = new DtcDecoderService();

    @Test
    void decodesKnownGenericPowertrainCode() {
        Optional<String> result = decoder.decode("P0301");
        assertTrue(result.isPresent());
        assertEquals("Cylinder 1 Misfire Detected", result.get());
    }

    @Test
    void decodesAnotherKnownCodeCaseInsensitively() {
        Optional<String> result = decoder.decode("p0420");
        assertTrue(result.isPresent());
        assertEquals("Catalyst System Efficiency Below Threshold (Bank 1)", result.get());
    }

    @Test
    void fallsBackToSystemDescriptionForUnknownGenericCode() {
        Optional<String> result = decoder.decode("P0890");
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("Powertrain fault"));
        assertTrue(result.get().contains("generic code P0890"));
    }

    @Test
    void describesManufacturerSpecificCode() {
        Optional<String> result = decoder.decode("P1234");
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("manufacturer-specific"));
    }

    @Test
    void describesChassisAndBodySystems() {
        assertTrue(decoder.decode("C1234").get().contains("Chassis fault"));
        assertTrue(decoder.decode("B1318").get().contains("Body fault"));
        assertTrue(decoder.decode("U0100").get().contains("Network"));
    }

    @Test
    void rejectsMalformedCodes() {
        assertFalse(decoder.decode("XYZ").isPresent());
        assertFalse(decoder.decode("P030").isPresent());      // too short
        assertFalse(decoder.decode("P03010").isPresent());    // too long
        assertFalse(decoder.decode(null).isPresent());
        assertFalse(decoder.decode("  ").isPresent());
    }
}

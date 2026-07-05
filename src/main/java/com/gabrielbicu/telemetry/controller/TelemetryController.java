package com.gabrielbicu.telemetry.controller;

import com.gabrielbicu.telemetry.dto.TelemetryReadingRequest;
import com.gabrielbicu.telemetry.dto.TelemetryReadingResponse;
import com.gabrielbicu.telemetry.service.TelemetryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * Central ingestion endpoint.
 *
 * <p>{@code POST /api/telemetry} is the hot path of the platform: every connected
 * car sends one reading per second of driving through it. The current impl is
 * synchronous (DB write blocks the response). Week 6's stretch goal decouples
 * this with a Kafka producer so the endpoint can ack immediately and a consumer
 * persists asynchronously.
 */
@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @PostMapping
    public ResponseEntity<TelemetryReadingResponse> ingestReading(
            @Valid @RequestBody TelemetryReadingRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        TelemetryReadingResponse saved = telemetryService.ingestReading(request, userId);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saved.getId())
                .toUri();
        return ResponseEntity.created(location).body(saved);
    }
}

package com.gabrielbicu.telemetry.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Simple health-check endpoint.
 *
 * <p>Useful for:
 * <ul>
 *   <li>Load balancers / orchestrators (Docker healthcheck, Kubernetes liveness probe)</li>
 *   <li>Verifying the app started and is reachable after boot</li>
 *   <li>A first sanity-check target when integrating the API</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * Response body for the health check.
     *
     * <p>A {@code record} is an immutable data carrier — Java auto-generates the
     * constructor, accessors, equals/hashCode/toString. Ideal for JSON DTOs.
     */
    public record HealthStatus(String status, Instant timestamp) {
        static HealthStatus up() {
            return new HealthStatus("UP", Instant.now());
        }
    }

    @GetMapping("/health")
    public HealthStatus health() {
        return HealthStatus.up();
    }

}

package com.gabrielbicu.telemetry.service;

import com.gabrielbicu.telemetry.dto.TelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumes the {@code telemetry.readings} topic and feeds the in-memory live
 * buffer ({@link LiveTelemetryService}).
 *
 * <p>This is the "decoupled" half of the ingestion flow: the HTTP request
 * path persists to Postgres and returns immediately; this consumer does the
 * downstream work (maintaining the live model) asynchronously, independent of
 * request latency. If Kafka is down at startup the listener simply fails to
 * connect and retries in the background — the app and the synchronous ingestion
 * keep working, so this is purely additive.
 */
@Service
public class TelemetryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryEventConsumer.class);

    private final LiveTelemetryService liveTelemetryService;

    public TelemetryEventConsumer(LiveTelemetryService liveTelemetryService) {
        this.liveTelemetryService = liveTelemetryService;
    }

    @KafkaListener(
            topics = "telemetry.readings",
            groupId = "${spring.kafka.consumer.group-id}")
    public void consume(TelemetryEvent event) {
        if (event == null) {
            return;
        }
        liveTelemetryService.record(event);
        log.debug("Consumed reading {} for vehicle {}", event.readingId(), event.vehicleId());
    }
}

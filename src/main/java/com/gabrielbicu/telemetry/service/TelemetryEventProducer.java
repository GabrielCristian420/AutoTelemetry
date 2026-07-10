package com.gabrielbicu.telemetry.service;

import com.gabrielbicu.telemetry.domain.TelemetryReading;
import com.gabrielbicu.telemetry.dto.TelemetryEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes each ingested reading to the {@code telemetry.readings} topic.
 *
 * <p>Design notes (worth articulating at interview):
 * <ul>
 *   <li><b>Fire-and-forget, non-blocking.</b> We attach an async callback that
 *       only logs on failure. The HTTP response is already committed by the
 *       time this runs, so a slow/broken broker never delays or fails ingest.
 *   <li><b>Idempotent key.</b> The producer key is the reading id, so Kafka can
 *       de-duplicate retries and preserve per-reading ordering on the topic.
 *   <li><b>Graceful degradation.</b> If Kafka is unavailable the send future
 *       fails and we log — ingestion itself (which persists synchronously)
 *       keeps working. This is why the app boots fine even without a broker.
 * </ul>
 */
@Service
public class TelemetryEventProducer {

    public static final String TOPIC = "telemetry.readings";

    private static final Logger log = LoggerFactory.getLogger(TelemetryEventProducer.class);

    private final KafkaTemplate<String, TelemetryEvent> kafkaTemplate;

    public TelemetryEventProducer(KafkaTemplate<String, TelemetryEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishReading(TelemetryReading reading, Long vehicleId, Long userId) {
        TelemetryEvent event = new TelemetryEvent(
                reading.getId(),
                reading.getTrip() != null ? reading.getTrip().getId() : null,
                vehicleId,
                userId,
                reading.getRecordedAt(),
                reading.getSpeedKmh(),
                reading.getRpm(),
                reading.getEngineTempC(),
                reading.getFuelLevelPct(),
                reading.getDtcCodes().stream().map(c -> c.getCode()).toList()
        );

        CompletableFuture<SendResult<String, TelemetryEvent>> future =
                kafkaTemplate.send(new ProducerRecord<>(TOPIC, String.valueOf(reading.getId()), event));

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                // Best-effort: never let a Kafka outage bubble up to the caller.
                if (ex instanceof TimeoutException) {
                    log.warn("Kafka send timed out for reading {} (broker unreachable?)", reading.getId());
                } else {
                    log.warn("Kafka send failed for reading {}: {}", reading.getId(), ex.getMessage());
                }
            } else {
                log.debug("Published reading {} to topic {}", reading.getId(), TOPIC);
            }
        });
    }
}

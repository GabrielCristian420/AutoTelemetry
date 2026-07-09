package com.gabrielbicu.telemetry.config;

import com.gabrielbicu.telemetry.service.TelemetryEventProducer;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the Kafka topic used to stream ingested readings.
 *
 * <p>{@link NewTopic} is idempotent: if the topic already exists with the same
 * partitions/replication, Spring Kafka leaves it alone; otherwise it creates
 * it on startup. Partitioning by reading id (the producer key) means all
 * events for a given reading land on one partition and stay ordered.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic telemetryReadingsTopic() {
        return new NewTopic(TelemetryEventProducer.TOPIC, 3, (short) 1);
    }
}

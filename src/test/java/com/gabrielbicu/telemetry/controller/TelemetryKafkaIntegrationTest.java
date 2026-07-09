package com.gabrielbicu.telemetry.controller;

import com.gabrielbicu.telemetry.config.JwtUtil;
import com.gabrielbicu.telemetry.domain.User;
import com.gabrielbicu.telemetry.domain.Vehicle;
import com.gabrielbicu.telemetry.domain.Trip;
import com.gabrielbicu.telemetry.domain.TelemetryReading;
import com.gabrielbicu.telemetry.repository.UserRepository;
import com.gabrielbicu.telemetry.repository.VehicleRepository;
import com.gabrielbicu.telemetry.repository.TripRepository;
import com.gabrielbicu.telemetry.repository.TelemetryReadingRepository;
import com.gabrielbicu.telemetry.service.LiveTelemetryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of the Week 6 Kafka pipeline: a reading ingested over HTTP is
 * persisted (system of record) AND published to Kafka; the consumer then feeds
 * the in-memory live buffer, which the {@code /live} endpoint reads.
 *
 * <p>Runs against real PostgreSQL + Kafka containers. Skipped automatically when
 * Docker is unavailable ({@code disabledWithoutDocker = true}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class TelemetryKafkaIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("autotelemetry")
            .withUsername("autotelemetry")
            .withPassword("autotelemetry");

    @Container
    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private TelemetryReadingRepository readingRepository;
    @Autowired private LiveTelemetryService liveTelemetryService;
    @Autowired private JwtUtil jwtUtil;

    @Test
    void ingestedReading_flowsThroughKafkaToLiveBuffer() throws Exception {
        // Seed user + vehicle + trip.
        User user = new User();
        user.setEmail("kafka@example.com");
        user.setPasswordHash("hash");
        user.setFullName("Kafka User");
        user.setRole(User.Role.USER);
        User savedUser = userRepository.save(user);
        String token = jwtUtil.generateToken(savedUser.getId(), "kafka@example.com");

        Vehicle vehicle = new Vehicle();
        vehicle.setVin("WBA7E2C50KG876599");
        vehicle.setMake("BMW");
        vehicle.setModel("330i");
        vehicle.setYear(2021);
        vehicle.setPlate("B999XYZ");
        vehicle.setUser(savedUser);
        Vehicle savedVehicle = vehicleRepository.save(vehicle);

        Trip trip = new Trip();
        trip.setVehicle(savedVehicle);
        trip.setStartedAt(Instant.now().minusSeconds(600));
        Trip savedTrip = tripRepository.save(trip);

        // Ingest a reading over HTTP (registers it + publishes to Kafka).
        String readingJson = """
                {
                  "tripId": %d,
                  "recordedAt": "2024-05-01T10:00:00Z",
                  "speedKmh": 88.5,
                  "rpm": 3200,
                  "fuelLevelPct": 73.0,
                  "dtcCodes": ["P0301"]
                }
                """.formatted(savedTrip.getId());

        mockMvc.perform(post("/api/telemetry")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(readingJson))
                .andExpect(status().isCreated());

        // The synchronous DB write is immediate.
        await().atMost(Duration.ofSeconds(5))
                .until(() -> readingRepository.count() == 1);

        // The async Kafka consumer populates the live buffer (eventual).
        await().atMost(Duration.ofSeconds(20))
                .until(() -> liveTelemetryService.getRecent(savedVehicle.getId()).size() == 1);

        // The /live endpoint exposes the buffered reading (ownership-checked).
        mockMvc.perform(get("/api/vehicles/{id}/live", savedVehicle.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].speedKmh", is(88.5)))
                .andExpect(jsonPath("$[0].rpm", is(3200)))
                .andExpect(jsonPath("$[0].dtcCodes[0]", is("P0301")));
    }
}

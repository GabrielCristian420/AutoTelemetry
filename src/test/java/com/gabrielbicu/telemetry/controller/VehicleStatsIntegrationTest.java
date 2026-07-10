package com.gabrielbicu.telemetry.controller;

import com.gabrielbicu.telemetry.domain.User;
import com.gabrielbicu.telemetry.domain.Vehicle;
import com.gabrielbicu.telemetry.domain.Trip;
import com.gabrielbicu.telemetry.domain.TelemetryReading;
import com.gabrielbicu.telemetry.repository.UserRepository;
import com.gabrielbicu.telemetry.repository.VehicleRepository;
import com.gabrielbicu.telemetry.repository.TripRepository;
import com.gabrielbicu.telemetry.repository.TelemetryReadingRepository;
import com.gabrielbicu.telemetry.config.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@EnableAutoConfiguration(exclude = {
        KafkaAutoConfiguration.class
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class VehicleStatsIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("autotelemetry")
            .withUsername("autotelemetry")
            .withPassword("autotelemetry");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private com.gabrielbicu.telemetry.service.TelemetryEventProducer telemetryEventProducer;

    @MockBean
    private com.gabrielbicu.telemetry.service.TelemetryEventConsumer telemetryEventConsumer;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TelemetryReadingRepository readingRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private Long testUserId;
    private Long testVehicleId;
    private String token;

    @BeforeEach
    void setUp() {
        readingRepository.deleteAll();
        tripRepository.deleteAll();
        vehicleRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setEmail("owner@example.com");
        user.setPasswordHash("hashedpassword");
        user.setFullName("Owner Name");
        user.setRole(User.Role.USER);
        User savedUser = userRepository.save(user);
        testUserId = savedUser.getId();

        token = jwtUtil.generateToken(testUserId, "owner@example.com");

        Vehicle vehicle = new Vehicle();
        vehicle.setVin("WBA7E2C50KG876543");
        vehicle.setMake("BMW");
        vehicle.setModel("330i");
        vehicle.setYear(2020);
        vehicle.setPlate("B123ABC");
        vehicle.setUser(savedUser);
        Vehicle savedVehicle = vehicleRepository.save(vehicle);
        testVehicleId = savedVehicle.getId();
    }

    @Test
    void getVehicleStats_returnsAggregatedMetrics() throws Exception {
        Trip trip = new Trip();
        trip.setVehicle(vehicleRepository.findById(testVehicleId).orElseThrow());
        trip.setStartedAt(Instant.now().minusSeconds(3600));
        Trip savedTrip = tripRepository.save(trip);

        TelemetryReading reading1 = new TelemetryReading();
        reading1.setTrip(savedTrip);
        reading1.setRecordedAt(Instant.now().minusSeconds(10));
        reading1.setSpeedKmh(60.0);
        reading1.setRpm(3000);
        reading1.setFuelLevelPct(80.0);
        readingRepository.save(reading1);

        TelemetryReading reading2 = new TelemetryReading();
        reading2.setTrip(savedTrip);
        reading2.setRecordedAt(Instant.now().minusSeconds(5));
        reading2.setSpeedKmh(70.0);
        reading2.setRpm(3500);
        reading2.setFuelLevelPct(78.0);
        readingRepository.save(reading2);

        mockMvc.perform(get("/api/vehicles/{id}/stats", testVehicleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avgSpeedKmh", is(65.0)))
                .andExpect(jsonPath("$.maxRpm", is(3500)))
                .andExpect(jsonPath("$.totalFuelDropPct", is(2.0)))
                .andExpect(jsonPath("$.activeDtcCount", is(0)));
    }

    @Test
    void getVehicleStats_withNoReadings_returnsAllZeros() throws Exception {
        mockMvc.perform(get("/api/vehicles/{id}/stats", testVehicleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avgSpeedKmh", is(0.0)))
                .andExpect(jsonPath("$.maxRpm", is(0)))
                .andExpect(jsonPath("$.totalFuelDropPct", is(0.0)))
                .andExpect(jsonPath("$.activeDtcCount", is(0)));
    }

    @Test
    void getReadingsForTrip_returnsPaginatedReadings() throws Exception {
        Trip trip = new Trip();
        trip.setVehicle(vehicleRepository.findById(testVehicleId).orElseThrow());
        trip.setStartedAt(Instant.now().minusSeconds(3600));
        Trip savedTrip = tripRepository.save(trip);

        TelemetryReading reading1 = new TelemetryReading();
        reading1.setTrip(savedTrip);
        reading1.setRecordedAt(Instant.now().minusSeconds(30));
        reading1.setSpeedKmh(50.0);
        reading1.setRpm(2500);
        reading1.setFuelLevelPct(90.0);
        readingRepository.save(reading1);

        TelemetryReading reading2 = new TelemetryReading();
        reading2.setTrip(savedTrip);
        reading2.setRecordedAt(Instant.now().minusSeconds(20));
        reading2.setSpeedKmh(60.0);
        reading2.setRpm(3000);
        reading2.setFuelLevelPct(89.0);
        readingRepository.save(reading2);

        TelemetryReading reading3 = new TelemetryReading();
        reading3.setTrip(savedTrip);
        reading3.setRecordedAt(Instant.now().minusSeconds(10));
        reading3.setSpeedKmh(70.0);
        reading3.setRpm(3500);
        reading3.setFuelLevelPct(88.0);
        readingRepository.save(reading3);

        mockMvc.perform(get("/api/trips/{id}/readings", savedTrip.getId())
                        .param("page", "0")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", is(2)))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.totalPages", is(2)))
                .andExpect(jsonPath("$.content[0].speedKmh", is(50.0)))
                .andExpect(jsonPath("$.content[1].speedKmh", is(60.0)));
    }
}

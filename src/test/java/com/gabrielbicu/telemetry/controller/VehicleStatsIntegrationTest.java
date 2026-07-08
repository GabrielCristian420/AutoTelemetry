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

import java.time.Instant;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
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
                .andExpect(jsonPath("$.totalFuelConsumed", is(2.0)))
                .andExpect(jsonPath("$.activeDtcCount", is(0)));
    }
}

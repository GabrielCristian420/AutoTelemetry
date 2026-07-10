package com.gabrielbicu.telemetry.controller;

import com.gabrielbicu.telemetry.config.JwtUtil;
import com.gabrielbicu.telemetry.domain.User;
import com.gabrielbicu.telemetry.dto.TelemetryReadingRequest;
import com.gabrielbicu.telemetry.dto.TelemetryReadingResponse;
import com.gabrielbicu.telemetry.service.TelemetryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sliced integration/validation tests for {@link TelemetryController}.
 */
@SpringBootTest
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        FlywayAutoConfiguration.class
})
@AutoConfigureMockMvc
class TelemetryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private com.gabrielbicu.telemetry.repository.UserRepository userRepository;
    @MockBean private TelemetryService telemetryService;

    // Stub out other repositories used in spring context creation/validation if necessary
    @MockBean private com.gabrielbicu.telemetry.repository.VehicleRepository vehicleRepository;
    @MockBean private com.gabrielbicu.telemetry.repository.TripRepository tripRepository;
    @MockBean private com.gabrielbicu.telemetry.repository.TelemetryReadingRepository telemetryReadingRepository;
    @MockBean private com.gabrielbicu.telemetry.repository.DtcCodeRepository dtcCodeRepository;

    private String validToken;
    private final Long testUserId = 42L;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(testUserId);
        user.setEmail("user@example.com");
        user.setRole(User.Role.USER);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(user));

        validToken = jwtUtil.generateToken(testUserId, "user@example.com");
    }

    @Test
    void ingestReading_success_returnsCreated() throws Exception {
        TelemetryReadingRequest request = TelemetryReadingRequest.builder()
                .tripId(10L)
                .recordedAt(Instant.now())
                .speedKmh(60.0)
                .rpm(2000)
                .fuelLevelPct(80.0)
                .engineTempC(90.0)
                .dtcCodes(List.of("P0301"))
                .build();

        TelemetryReadingResponse response = TelemetryReadingResponse.builder()
                .id(100L)
                .speedKmh(60.0)
                .rpm(2000)
                .build();

        when(telemetryService.ingestReading(any(TelemetryReadingRequest.class), eq(testUserId)))
                .thenReturn(response);

        mockMvc.perform(post("/api/telemetry")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100L))
                .andExpect(jsonPath("$.speedKmh").value(60.0))
                .andExpect(jsonPath("$.rpm").value(2000));
    }

    @Test
    void ingestReading_validation_nullTripId_returnsBadRequest() throws Exception {
        TelemetryReadingRequest request = TelemetryReadingRequest.builder()
                .tripId(null) // invalid
                .recordedAt(Instant.now())
                .build();

        mockMvc.perform(post("/api/telemetry")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0]").value("tripId: must not be null"));
    }

    @Test
    void ingestReading_validation_nullRecordedAt_returnsBadRequest() throws Exception {
        TelemetryReadingRequest request = TelemetryReadingRequest.builder()
                .tripId(10L)
                .recordedAt(null) // invalid
                .build();

        mockMvc.perform(post("/api/telemetry")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0]").value("recordedAt: must not be null"));
    }

    @Test
    void ingestReading_validation_negativeSpeed_returnsBadRequest() throws Exception {
        TelemetryReadingRequest request = TelemetryReadingRequest.builder()
                .tripId(10L)
                .recordedAt(Instant.now())
                .speedKmh(-1.5) // invalid
                .build();

        mockMvc.perform(post("/api/telemetry")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0]").value("speedKmh: must be greater than or equal to 0.0"));
    }

    @Test
    void ingestReading_validation_negativeRpm_returnsBadRequest() throws Exception {
        TelemetryReadingRequest request = TelemetryReadingRequest.builder()
                .tripId(10L)
                .recordedAt(Instant.now())
                .rpm(-10) // invalid
                .build();

        mockMvc.perform(post("/api/telemetry")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0]").value("rpm: must be greater than or equal to 0"));
    }

    @Test
    void ingestReading_validation_fuelLevelTooHigh_returnsBadRequest() throws Exception {
        TelemetryReadingRequest request = TelemetryReadingRequest.builder()
                .tripId(10L)
                .recordedAt(Instant.now())
                .fuelLevelPct(105.0) // invalid
                .build();

        mockMvc.perform(post("/api/telemetry")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0]").value("fuelLevelPct: must be less than or equal to 100.0"));
    }

    @Test
    void ingestReading_validation_engineTempTooLow_returnsBadRequest() throws Exception {
        TelemetryReadingRequest request = TelemetryReadingRequest.builder()
                .tripId(10L)
                .recordedAt(Instant.now())
                .engineTempC(-41.0) // invalid
                .build();

        mockMvc.perform(post("/api/telemetry")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0]").value("engineTempC: must be greater than or equal to -40.0"));
    }
}

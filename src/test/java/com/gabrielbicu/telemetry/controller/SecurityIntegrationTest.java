package com.gabrielbicu.telemetry.controller;

import com.gabrielbicu.telemetry.config.JwtUtil;
import com.gabrielbicu.telemetry.domain.User;
import com.gabrielbicu.telemetry.service.VehicleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the actual security wiring end-to-end: that a protected route is
 * unreachable without a token and reachable with a valid one.
 *
 * <p>Boots the full Spring context (so the real {@code SecurityFilterChain} and
 * {@link com.gabrielbicu.telemetry.config.JwtAuthFilter} are in play) but swaps
 * the database for mocks, so no PostgreSQL is required. A token is produced by
 * the real {@link JwtUtil} so the filter validates it exactly as in production.
 */
@SpringBootTest
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        FlywayAutoConfiguration.class,
        KafkaAutoConfiguration.class
})
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private com.gabrielbicu.telemetry.repository.UserRepository userRepository;

    @MockBean
    private com.gabrielbicu.telemetry.service.TelemetryEventProducer telemetryEventProducer;

    @MockBean
    private com.gabrielbicu.telemetry.service.TelemetryEventConsumer telemetryEventConsumer;

    @MockBean
    private com.gabrielbicu.telemetry.repository.VehicleRepository vehicleRepository;

    @MockBean
    private com.gabrielbicu.telemetry.repository.TripRepository tripRepository;

    @MockBean
    private com.gabrielbicu.telemetry.repository.TelemetryReadingRepository telemetryReadingRepository;

    @MockBean
    private com.gabrielbicu.telemetry.repository.DtcCodeRepository dtcCodeRepository;

    @MockBean
    private VehicleService vehicleService;

    private String tokenFor(Long userId, String email) {
        User user = new User();
        user.setId(userId);
        user.setEmail(email);
        user.setRole(User.Role.USER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        return jwtUtil.generateToken(userId, email);
    }

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/vehicles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withValidToken_returns200() throws Exception {
        String token = tokenFor(1L, "owner@example.com");
        when(vehicleService.listVehicles(1L)).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/vehicles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/vehicles")
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }
}

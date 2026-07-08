package com.gabrielbicu.telemetry.service;

import com.gabrielbicu.telemetry.config.JwtUtil;
import com.gabrielbicu.telemetry.domain.User;
import com.gabrielbicu.telemetry.dto.AuthResponse;
import com.gabrielbicu.telemetry.dto.RegisterRequest;
import com.gabrielbicu.telemetry.dto.LoginRequest;
import com.gabrielbicu.telemetry.exception.BusinessRuleException;
import com.gabrielbicu.telemetry.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthService} with Mockito — no Spring context, just the
 * service and its collaborators mocked.
 *
 * <p>These pin down the two security-critical behaviours that the integration
 * test can't easily isolate: the duplicate-email rule and the fact that *both*
 * "no such user" and "wrong password" collapse into the same
 * {@link AuthService.InvalidCredentialsException} (so we never leak which emails
 * are registered).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_success_returnsTokenWithUserId() {
        RegisterRequest request = RegisterRequest.builder()
                .email("new@example.com")
                .password("Password123")
                .fullName("New User")
                .build();

        User saved = new User();
        saved.setId(42L);
        saved.setEmail("new@example.com");
        saved.setFullName("New User");

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(passwordEncoder.encode("Password123")).thenReturn("hashed");
        when(jwtUtil.generateToken(42L, "new@example.com")).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        assertEquals("jwt-token", response.getToken());
        assertEquals(42L, response.getUserId());
        assertEquals("new@example.com", response.getEmail());
        // Password must be hashed before persisting, never stored raw.
        verify(passwordEncoder).encode("Password123");
        verify(userRepository).save(argThat(u -> "hashed".equals(u.getPasswordHash())
                && "new@example.com".equals(u.getEmail())));
    }

    @Test
    void register_duplicateEmail_throwsBusinessRuleException() {
        RegisterRequest request = RegisterRequest.builder()
                .email("taken@example.com")
                .password("Password123")
                .fullName("Taken")
                .build();

        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_success_returnsToken() {
        LoginRequest request = LoginRequest.builder()
                .email("user@example.com")
                .password("Password123")
                .build();

        User user = new User();
        user.setId(7L);
        user.setEmail("user@example.com");
        user.setPasswordHash("hashed");
        user.setFullName("Existing");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "hashed")).thenReturn(true);
        when(jwtUtil.generateToken(7L, "user@example.com")).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        assertEquals("jwt-token", response.getToken());
        assertEquals(7L, response.getUserId());
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {
        LoginRequest request = LoginRequest.builder()
                .email("user@example.com")
                .password("WrongPass")
                .build();

        User user = new User();
        user.setId(7L);
        user.setPasswordHash("hashed");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass", "hashed")).thenReturn(false);

        assertThrows(AuthService.InvalidCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void login_unknownEmail_throwsInvalidCredentials_sameAsWrongPassword() {
        LoginRequest request = LoginRequest.builder()
                .email("ghost@example.com")
                .password("whatever")
                .build();

        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThrows(AuthService.InvalidCredentialsException.class, () -> authService.login(request));
    }
}

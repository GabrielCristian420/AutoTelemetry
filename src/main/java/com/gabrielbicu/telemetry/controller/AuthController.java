package com.gabrielbicu.telemetry.controller;

import com.gabrielbicu.telemetry.dto.AuthResponse;
import com.gabrielbicu.telemetry.dto.LoginRequest;
import com.gabrielbicu.telemetry.dto.RegisterRequest;
import com.gabrielbicu.telemetry.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/auth/register} and {@code POST /api/auth/login}.
 *
 * <p>Both are public (see SecurityConfig: {@code /api/auth/**} permitAll).
 * Both return the JWT in the response body — returning it immediately on
 * register saves a round trip, the client doesn't need to log in again.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}

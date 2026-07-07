package com.gabrielbicu.telemetry.service;

import com.gabrielbicu.telemetry.config.JwtUtil;
import com.gabrielbicu.telemetry.domain.User;
import com.gabrielbicu.telemetry.dto.AuthResponse;
import com.gabrielbicu.telemetry.dto.LoginRequest;
import com.gabrielbicu.telemetry.dto.RegisterRequest;
import com.gabrielbicu.telemetry.exception.BusinessRuleException;
import com.gabrielbicu.telemetry.exception.EntityNotFoundException;
import com.gabrielbicu.telemetry.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plain register / login flow backed by the {@link User} table.
 *
 * <p>Two notes worth articulating:
 * <ul>
 *   <li><b>Why BCrypt matches and not a home-grown hash.</b> {@link PasswordEncoder#matches}
 *       is constant-time and BCrypt has built-in salt — implementing either
 *       manually is a well-known source of timing attacks. Don't roll your own.
 *   <li><b>Why we don't disclose "wrong password" vs "no such user".</b> Both
 *       login failure paths produce the same generic "invalid credentials"
 *       message; returning separate messages leaks whether an email is
 *       registered, which is a useful signal for attackers enumerating accounts.
 * </ul>
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessRuleException("Email already registered");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        // Default role USER is set on the entity itself (User.role = Role.USER).

        User saved = userRepository.save(user);
        return toAuthResponse(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return toAuthResponse(user);
    }

    private AuthResponse toAuthResponse(User user) {
        String token = jwtUtil.generateToken(user.getId(), user.getEmail());
        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build();
    }

    /**
     * Thrown for both "no such user" and "wrong password" — the message is the
     * same so the response shape is identical and leaks nothing about which
     * emails are registered.
     */
    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Invalid email or password");
        }
    }
}

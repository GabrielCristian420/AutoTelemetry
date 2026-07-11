package com.gabrielbicu.telemetry.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring Security wiring for a stateless JWT API.
 *
 * <p>Key decisions (all worth articulating at interview):
 * <ul>
 *   <li><b>CSRF disabled.</b> We never use cookies for auth, so the CSRF
 *       attack vector (a foreign site tricking the browser into sending auth
 *       cookies) does not apply. The JWT lives in an Authorization header
 *       that a cross-origin request cannot set without CORS allowing it.
 *   <li><b>Stateless session.</b> Spring Security creates an HTTP session by
 *       default; we explicitly turn that off because every request is
 *       authenticated from scratch via the JWT, not via a server-side session.
 *   <li><b>Public routes:</b> {@code /api/auth/**} (register/login) and
 *       {@code /api/health} (liveness probe). Everything else needs auth.
 *   <li><b>Our filter before the form-login filter.</b> We swap our
 *       {@link JwtAuthFilter} in front of Spring's own
 *       {@link UsernamePasswordAuthenticationFilter} so JWT validation runs
 *       first.
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/api/health").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        // Missing/invalid token = unauthenticated -> 401 (not the framework default 403).
                        // The body matches the ApiError shape used by GlobalExceptionHandler.
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write("{\"status\":401,\"message\":\"Unauthorized\"}");
                        }))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** BCrypt is the de-facto standard password hasher for Spring apps. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS config so the React dashboard (Vite dev server on :5173) can call
     * this API (on :8080). Browser Same-Origin Policy blocks cross-origin
     * fetches without this. We pin an explicit origin (not {@code *}) because
     * credentials are allowed — a wildcard origin is rejected when
     * {@code allowCredentials} is true.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

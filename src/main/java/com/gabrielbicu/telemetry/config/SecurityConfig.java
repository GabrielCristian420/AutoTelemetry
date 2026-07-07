package com.gabrielbicu.telemetry.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/api/health").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** BCrypt is the de-facto standard password hasher for Spring apps. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

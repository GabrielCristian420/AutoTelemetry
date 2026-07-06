package com.gabrielbicu.telemetry.config;

import com.gabrielbicu.telemetry.domain.User;
import com.gabrielbicu.telemetry.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Picks up Bearer JWTs from the Authorization header and loads the caller's
 * identity into the {@link SecurityContextHolder}.
 *
 * <p>The filter is intentionally permissive: if the header is missing or the
 * token is invalid, it leaves the context empty and lets the request continue.
 * Whether that constitutes a 401 is decided by the SecurityConfig ({@code anyRequest().authenticated()}
 * will reject), not by this filter. That keeps the auth and authorization
 * concerns separate and avoids hardcoding 401 here.
 *
 * <p>It is added once per request ({@link OncePerRequestFilter}), not once per
 * forward — important because servlet filters otherwise run again on async
 * dispatches and we'd authenticate twice.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        if (!jwtUtil.isValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        Long userId = jwtUtil.extractUserId(token);
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            // Token references a user that no longer exists — treat as unauthenticated.
            filterChain.doFilter(request, response);
            return;
        }

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        var authentication = new UsernamePasswordAuthenticationToken(user.getId(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}

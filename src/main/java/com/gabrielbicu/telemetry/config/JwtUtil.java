package com.gabrielbicu.telemetry.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Builds and parses JWT tokens.
 *
 * <p>The token carries the user's id and email as custom claims, signed with an
 * HMAC-SHA key. The signing key is read from the {@code app.jwt.secret}
 * property (defaulting to a throwaway dev value); in production it must come
 * from an environment variable so the secret isn't checked into the repo.
 *
 * <p>Why HMAC and not RSA? For a single backend that both issues and validates
 * the tokens (no separate issuer/verifier), a symmetric key is enough and
 * cheaper than managing key pairs. Asymmetric makes sense when the verifier is
 * a different service (e.g. a gateway) that only needs the public key.
 */
@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtUtil(@Value("${app.jwt.secret:dev-secret-please-override-in-prod-only-for-local-testing}") String secret,
                   @Value("${app.jwt.expiration-minutes:60}") long expirationMinutes) {
        // HMAC-SHA requires a key at least 256 bits (32 bytes) long.
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMillis = expirationMinutes * 60_000L;
    }

    public String generateToken(Long userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMillis)))
                .signWith(signingKey)
                .compact();
    }

    public Long extractUserId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }

    public String extractEmail(String token) {
        return parse(token).get("email", String.class);
    }

    /**
     * Returns true if the token is syntactically valid, signed by us, and not
     * expired. Any failure (tampering, wrong key, expired) surfaces as a
     * {@link JwtException}, so a single try/catch in the caller is enough.
     */
    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

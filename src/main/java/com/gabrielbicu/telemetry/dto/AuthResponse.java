package com.gabrielbicu.telemetry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response body for both {@code /register} and {@code /login}. Returning the
 * token immediately after registration removes one round-trip for the client;
 * they don't need a separate login after signing up.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String token;
    private Long userId;
    private String email;
    private String fullName;
}

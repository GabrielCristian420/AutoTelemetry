package com.gabrielbicu.telemetry.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for {@code POST /api/auth/register}.
 *
 * <p>Password is validated by length only here. A simple minimum is enough to
 * prevent empty passwords; a full strength policy (mixed case, digits, etc.) is
 * product-level concern, not an API-level one — leave it out of the code that
 * pair-programs with CV reviewers.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

    @Email
    @NotBlank
    @Size(max = 255)
    private String email;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;

    @NotBlank
    @Size(max = 255)
    private String fullName;
}

package com.hyf.agent_work_foot.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthRequests {
    private AuthRequests() { }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{6,20}$") String password,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{6,20}$") String confirmPassword) { }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{6,20}$") String password) { }

    public record RefreshTokenRequest(@NotBlank String refreshToken) { }
}

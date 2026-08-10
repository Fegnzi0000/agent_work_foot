package com.hyf.agent_work_foot.auth;

public final class AuthResponses {
    private AuthResponses() { }

    public record UserData(String id, String email, String nickname, String avatarUrl, String role,
                           String status, boolean onboardingCompleted, boolean mustChangePassword) { }

    public record AuthData(String accessToken, long accessTokenExpiresIn, String refreshToken,
                           long refreshTokenExpiresIn, UserData user, String nextStep) { }

    public record TokenData(String accessToken, long accessTokenExpiresIn, String refreshToken,
                            long refreshTokenExpiresIn) { }
}

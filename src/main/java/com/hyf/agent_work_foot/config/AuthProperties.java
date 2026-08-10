package com.hyf.agent_work_foot.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(Jwt jwt, RateLimit rateLimit) {
    public record Jwt(String issuer, String activeKeyId, String activeSecret, String previousKeyId,
                      String previousSecret, Duration accessTokenTtl, Duration refreshTokenTtl) { }

    public record RateLimit(int loginMaxAttempts, Duration loginWindow, int registerMaxAttempts,
                            Duration registerWindow, int refreshMaxAttempts, Duration refreshWindow) { }
}

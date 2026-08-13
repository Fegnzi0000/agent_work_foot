package com.hyf.agent_work_foot.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证模块的外部配置映射。
 *
 * <p>从 YAML 或环境变量读取 JWT 密钥环、Token 有效期和限流阈值；只承载配置，不实现签发或限流逻辑。</p>
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(Jwt jwt, RateLimit rateLimit) {
    /** JWT 密钥环和 Token 生命周期配置；上一把密钥只用于校验存量 Access Token。 */
    public record Jwt(
            String issuer,
            String activeKeyId,
            String activeSecret,
            String previousKeyId,
            String previousSecret,
            Duration accessTokenTtl,
            Duration refreshTokenTtl
    ) {
    }

    /** 登录、注册和刷新 Token 接口的固定窗口限流配置。 */
    public record RateLimit(
            int loginMaxAttempts,
            Duration loginWindow,
            int registerMaxAttempts,
            Duration registerWindow,
            int refreshMaxAttempts,
            Duration refreshWindow
    ) {
    }
}

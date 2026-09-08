package com.hyf.agent_work_foot.auth;

import com.hyf.agent_work_foot.config.AuthProperties;
import com.hyf.agent_work_foot.config.RedisRateLimitSupport;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 认证接口的 Redis 共享限流实现。 */
@Component
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
public class RedisAuthRateLimiter implements AuthRateLimiter {
    private final AuthProperties.RateLimit limits;
    private final RedisRateLimitSupport limiter;

    public RedisAuthRateLimiter(AuthProperties properties, RedisRateLimitSupport limiter) {
        this.limits = properties.rateLimit();
        this.limiter = limiter;
    }

    @Override public void checkLogin(String ip, String email) { limiter.check("login", List.of(ip, email), limits.loginMaxAttempts(), limits.loginWindow()); }
    @Override public void checkWeChatLogin(String ip) { limiter.check("wechat-login", List.of(ip), limits.loginMaxAttempts(), limits.loginWindow()); }
    @Override public void checkRegistration(String ip) { limiter.check("register", List.of(ip), limits.registerMaxAttempts(), limits.registerWindow()); }
    @Override public void checkRefresh(String ip) { limiter.check("refresh", List.of(ip), limits.refreshMaxAttempts(), limits.refreshWindow()); }
}

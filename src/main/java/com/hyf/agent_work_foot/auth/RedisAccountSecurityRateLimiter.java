package com.hyf.agent_work_foot.auth;

import com.hyf.agent_work_foot.config.AccountSecurityProperties;
import com.hyf.agent_work_foot.config.RedisRateLimitSupport;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 账号注销等敏感操作的 Redis 共享限流实现。 */
@Component
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
public class RedisAccountSecurityRateLimiter implements AccountSecurityRateLimiter {
    private final AccountSecurityProperties properties;
    private final RedisRateLimitSupport limiter;

    public RedisAccountSecurityRateLimiter(AccountSecurityProperties properties, RedisRateLimitSupport limiter) {
        this.properties = properties;
        this.limiter = limiter;
    }

    @Override public void check(String userId, String ip) {
        limiter.check("account-security", List.of(userId, ip), properties.rateLimit().maxAttempts(), properties.rateLimit().window());
    }
}

package com.hyf.agent_work_foot.admin;

import com.hyf.agent_work_foot.config.AdminProperties;
import com.hyf.agent_work_foot.config.RedisRateLimitSupport;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 保留给管理员敏感操作的 Redis 共享限流实现。 */
@Component
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
public class RedisAdminRateLimiter implements AdminRateLimiter {
    private final AdminProperties properties;
    private final RedisRateLimitSupport limiter;

    public RedisAdminRateLimiter(AdminProperties properties, RedisRateLimitSupport limiter) {
        this.properties = properties;
        this.limiter = limiter;
    }

    @Override public void checkTemporaryPassword(String adminUserId, String targetUserId, String ipAddress) {
        AdminProperties.RateLimit limits = properties.rateLimit();
        limiter.check("admin-target", List.of(adminUserId, targetUserId, ipAddress), limits.targetMaxAttempts(), limits.targetWindow());
        limiter.check("admin", List.of(adminUserId, ipAddress), limits.adminMaxAttempts(), limits.adminWindow());
    }
}

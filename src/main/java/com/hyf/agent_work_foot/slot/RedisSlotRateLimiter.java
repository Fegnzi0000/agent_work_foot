package com.hyf.agent_work_foot.slot;

import com.hyf.agent_work_foot.config.RedisRateLimitSupport;
import com.hyf.agent_work_foot.config.SlotProperties;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 老虎机抽取的 Redis 用户维度共享限流实现。 */
@Component
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
public class RedisSlotRateLimiter implements SlotRateLimiter {
    private final SlotProperties properties;
    private final RedisRateLimitSupport limiter;

    public RedisSlotRateLimiter(SlotProperties properties, RedisRateLimitSupport limiter) {
        this.properties = properties;
        this.limiter = limiter;
    }

    @Override public void check(String userId) {
        limiter.check("slot", List.of(userId), properties.rateLimit().maxAttempts(), properties.rateLimit().window());
    }
}

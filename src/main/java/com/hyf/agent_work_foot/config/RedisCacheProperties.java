package com.hyf.agent_work_foot.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Redis 读缓存配置；缓存不可用时业务统一回源 MySQL。 */
@ConfigurationProperties(prefix = "app.cache.redis")
public record RedisCacheProperties(
        boolean enabled,
        Duration foodPoolTtl,
        Duration preferenceOptionsTtl,
        Duration adminDashboardTtl
) {
}

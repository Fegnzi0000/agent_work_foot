package com.hyf.agent_work_foot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Redis 分布式限流开关与键摘要密钥；不承载业务缓存配置。 */
@ConfigurationProperties(prefix = "app.redis")
public record RedisRateLimitProperties(boolean enabled, String keySecret) {
}

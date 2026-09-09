package com.hyf.agent_work_foot.config;

import com.hyf.agent_work_foot.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Redis 原子固定窗口限流与 Redis 故障时的保守本机兜底。 */
@Component
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
public class RedisRateLimitSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisRateLimitSupport.class);
    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]) "
                    + "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[2]) end "
                    + "if count > tonumber(ARGV[1]) then return 0 end "
                    + "return 1",
            Long.class
    );

    private final StringRedisTemplate redis;
    private final byte[] keySecret;
    private final Clock clock;
    private final ConcurrentHashMap<String, LocalWindow> fallbackWindows = new ConcurrentHashMap<>();

    public RedisRateLimitSupport(StringRedisTemplate redis, RedisRateLimitProperties properties, Clock clock) {
        if (properties.keySecret() == null || properties.keySecret().isBlank()) {
            throw new IllegalStateException("启用 Redis 限流时必须配置 app.redis.key-secret");
        }
        this.redis = redis;
        this.keySecret = properties.keySecret().getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    /** 按命名空间、敏感标识和固定窗口消费一次配额。 */
    public void check(String namespace, List<String> identifiers, int maxAttempts, Duration window) {
        if (maxAttempts < 1 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Redis 限流窗口配置不合法");
        }
        String key = key(namespace, identifiers);
        try {
            Long allowed = redis.execute(INCREMENT_WITH_TTL, List.of(key), String.valueOf(maxAttempts),
                    String.valueOf(window.toMillis()));
            if (!Long.valueOf(1L).equals(allowed)) {
                throw limited();
            }
        } catch (DataAccessException exception) {
            LOGGER.warn("[Redis限流] Redis 不可用，已使用本机保守兜底 type={}", exception.getClass().getSimpleName());
            fallbackCheck(key, maxAttempts, window);
        }
    }

    private String key(String namespace, List<String> identifiers) {
        String normalizedNamespace = namespace == null ? "" : namespace.replaceAll("[^a-z0-9-]", "-");
        if (normalizedNamespace.isBlank() || identifiers == null || identifiers.isEmpty()) {
            throw new IllegalArgumentException("Redis 限流键参数不合法");
        }
        StringBuilder key = new StringBuilder("awf:v1:rl:").append(normalizedNamespace);
        for (String identifier : identifiers) {
            key.append(':').append(digest(identifier == null ? "" : identifier));
        }
        return key.toString();
    }

    private String digest(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySecret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("无法生成 Redis 限流键摘要", exception);
        }
    }

    private void fallbackCheck(String key, int maxAttempts, Duration window) {
        Instant now = clock.instant();
        LocalWindow current = fallbackWindows.compute(key, (unused, existing) -> existing == null
                || !now.isBefore(existing.startedAt().plus(window))
                ? new LocalWindow(now, 1)
                : new LocalWindow(existing.startedAt(), existing.count() + 1));
        if (current.count() > maxAttempts) {
            throw limited();
        }
    }

    private ApiException limited() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "请求过于频繁");
    }

    private record LocalWindow(Instant startedAt, int count) {
    }
}

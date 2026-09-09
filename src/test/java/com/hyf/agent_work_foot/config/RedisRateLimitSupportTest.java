package com.hyf.agent_work_foot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hyf.agent_work_foot.common.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

class RedisRateLimitSupportTest {
    private final StringRedisTemplate redis = org.mockito.Mockito.mock(StringRedisTemplate.class);
    private final RedisRateLimitSupport support = new RedisRateLimitSupport(redis,
            new RedisRateLimitProperties(true, "test-key-secret"),
            Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void usesHmacKeyAndPassesLimitAndWindowToLua() {
        when(redis.execute(any(DefaultRedisScript.class), anyList(), any(String.class), any(String.class)))
                .thenReturn(1L);

        support.check("login", List.of("203.0.113.7", "person@example.com"), 3, Duration.ofMinutes(1));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass((Class) List.class);
        verify(redis).execute(any(DefaultRedisScript.class), keys.capture(), org.mockito.ArgumentMatchers.eq("3"),
                org.mockito.ArgumentMatchers.eq("60000"));
        assertThat(keys.getValue()).hasSize(1);
        assertThat(keys.getValue().get(0))
                .startsWith("awf:v1:rl:login:")
                .doesNotContain("203.0.113.7")
                .doesNotContain("person@example.com");
    }

    @Test
    void rejectsWhenLuaReportsLimitExceeded() {
        when(redis.execute(any(DefaultRedisScript.class), anyList(), any(String.class), any(String.class)))
                .thenReturn(0L);

        assertThatThrownBy(() -> support.check("slot", List.of("user-1"), 1, Duration.ofMinutes(1)))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("RATE_LIMITED");
    }
}

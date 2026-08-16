package com.hyf.agent_work_foot.auth;

import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.config.AccountSecurityProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 单实例账号安全固定窗口限流器；多实例时可替换Redis或网关实现而不改变用户接口。 */
@Component
public class InMemoryAccountSecurityRateLimiter implements AccountSecurityRateLimiter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AccountSecurityProperties properties;
    private final Clock clock;

    /** 作用：创建限流器。输入：YAML配置和UTC时钟。输出：实例。逻辑：阈值不写入业务Service。 */
    public InMemoryAccountSecurityRateLimiter(AccountSecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** 作用：限制用户与IP组合键。输入：用户ID和IP。输出：允许时无返回。逻辑：修改密码和注销共享同一窗口计数。 */
    @Override
    public void check(String userId, String ip) {
        Instant now = clock.instant();
        String key = userId + ":" + ip;
        Window window = windows.computeIfAbsent(key, unused -> new Window(now, 0));
        synchronized (window) {
            if (!now.isBefore(window.startedAt.plus(properties.rateLimit().window()))) {
                window.startedAt = now;
                window.count = 0;
            }
            if (window.count >= properties.rateLimit().maxAttempts()) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "请求过于频繁");
            }
            window.count++;
        }
    }

    /** 单个用户与IP组合键的可变固定窗口，仅在对象监视器内访问。 */
    private static final class Window {
        private Instant startedAt;
        private int count;

        /** 作用：创建窗口状态。输入：开始时间和计数。输出：窗口实例。逻辑：后续只在同步块内修改。 */
        private Window(Instant startedAt, int count) {
            this.startedAt = startedAt;
            this.count = count;
        }
    }
}

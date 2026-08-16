package com.hyf.agent_work_foot.admin;

import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.config.AdminProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 单实例管理员临时密码固定窗口限流器，分别限制管理员总量和管理员到目标账号的频率。 */
@Component
public class InMemoryAdminRateLimiter implements AdminRateLimiter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AdminProperties properties;
    private final Clock clock;

    /** 作用：注入限流配置和时钟。输入：YAML配置、UTC时钟。输出：限流器实例。逻辑：业务服务不保存阈值。 */
    public InMemoryAdminRateLimiter(AdminProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** 作用：执行两个限流维度。输入：管理员、目标和IP。输出：允许时无返回。逻辑：任一窗口超限都返回统一429。 */
    @Override
    public void checkTemporaryPassword(String adminUserId, String targetUserId, String ipAddress) {
        AdminProperties.RateLimit config = properties.rateLimit();
        check("target:" + adminUserId + ':' + targetUserId + ':' + ipAddress,
                config.targetMaxAttempts(), config.targetWindow());
        check("admin:" + adminUserId + ':' + ipAddress,
                config.adminMaxAttempts(), config.adminWindow());
    }

    /** 作用：更新一个固定窗口。输入：键、阈值和窗口时长。输出：无。逻辑：窗口过期清零，达到阈值抛RATE_LIMITED。 */
    private void check(String key, int maxAttempts, Duration duration) {
        Instant now = clock.instant();
        Window window = windows.computeIfAbsent(key, unused -> new Window(now));
        synchronized (window) {
            if (!now.isBefore(window.startedAt.plus(duration))) {
                window.startedAt = now;
                window.count = 0;
            }
            if (window.count >= maxAttempts) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "请求过于频繁");
            }
            window.count++;
        }
    }

    /** 单个限流键的进程内可变窗口，只在对象监视器内读写。 */
    private static final class Window {
        private Instant startedAt;
        private int count;

        /** 作用：创建空窗口。输入：开始时间。输出：窗口实例。逻辑：初始计数为0。 */
        private Window(Instant startedAt) {
            this.startedAt = startedAt;
        }
    }
}

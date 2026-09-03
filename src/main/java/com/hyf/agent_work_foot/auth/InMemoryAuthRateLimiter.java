package com.hyf.agent_work_foot.auth;

import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.config.AuthProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 单实例环境下的固定窗口认证限流实现。
 *
 * <p>读取外部阈值配置并按键计数；不适用于多实例共享限流，部署扩展时应替换实现而不是修改 Controller。</p>
 */
@Component
public class InMemoryAuthRateLimiter implements AuthRateLimiter {
    private final AuthProperties.RateLimit limits;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    /** 作用：读取认证限流配置。输入：完整认证配置。输出：限流器实例。逻辑：保存其中的限流子配置。 */
    public InMemoryAuthRateLimiter(AuthProperties properties) {
        this.limits = properties.rateLimit();
    }

    /** 作用：校验登录频率。输入：IP 和邮箱。输出：允许时无返回，超限时抛 RATE_LIMITED。逻辑：构造组合键后检查窗口。 */
    @Override
    public void checkLogin(String ip, String email) {
        check("login:" + ip + ":" + email, limits.loginMaxAttempts(), limits.loginWindow());
    }

    /** 作用：校验微信登录频率。输入：IP。输出：允许时无返回，超限时抛异常。逻辑：避免攻击者用大量一次性code消耗后端与微信接口配额。 */
    @Override
    public void checkWeChatLogin(String ip) {
        check("wechat-login:" + ip, limits.loginMaxAttempts(), limits.loginWindow());
    }

    /** 作用：校验注册频率。输入：IP。输出：允许时无返回，超限时抛异常。逻辑：按注册键检查窗口。 */
    @Override
    public void checkRegistration(String ip) {
        check("register:" + ip, limits.registerMaxAttempts(), limits.registerWindow());
    }

    /** 作用：校验刷新频率。输入：IP。输出：允许时无返回，超限时抛异常。逻辑：按刷新键检查窗口。 */
    @Override
    public void checkRefresh(String ip) {
        check("refresh:" + ip, limits.refreshMaxAttempts(), limits.refreshWindow());
    }

    /**
     * 作用：在指定固定窗口内增加计数并判断是否超限。
     *
     * <p>输入：限流键、最大次数与窗口时长。输出：未超限时无返回，超限时抛异常。
     * 逻辑：原子更新并在窗口过期后重置计数，避免并发请求丢失次数。</p>
     */
    private void check(String key, int maximum, Duration duration) {
        Instant now = Instant.now();
        Window window = windows.compute(key, (unused, current) -> current == null
                || !current.startedAt().plus(duration).isAfter(now)
                ? new Window(now, 1)
                : new Window(current.startedAt(), current.count() + 1));
        if (window.count() > maximum) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "请求过于频繁");
        }
    }

    /** 单个限流键当前窗口的开始时间和已使用次数。 */
    private record Window(Instant startedAt, int count) {
    }
}

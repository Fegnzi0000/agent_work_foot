package com.hyf.agent_work_foot.auth;

import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.config.AuthProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class InMemoryAuthRateLimiter implements AuthRateLimiter {
    private final AuthProperties.RateLimit limits;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    public InMemoryAuthRateLimiter(AuthProperties properties) { this.limits = properties.rateLimit(); }
    public void checkLogin(String ip, String email) { check("login:" + ip + ":" + email, limits.loginMaxAttempts(), limits.loginWindow()); }
    public void checkRegistration(String ip) { check("register:" + ip, limits.registerMaxAttempts(), limits.registerWindow()); }
    public void checkRefresh(String ip) { check("refresh:" + ip, limits.refreshMaxAttempts(), limits.refreshWindow()); }
    private void check(String key, int maximum, Duration duration) {
        Instant now = Instant.now();
        Window window = windows.compute(key, (unused, current) -> current == null || !current.startedAt().plus(duration).isAfter(now)
                ? new Window(now, 1) : new Window(current.startedAt(), current.count() + 1));
        if (window.count() > maximum) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "请求过于频繁");
    }
    private record Window(Instant startedAt, int count) { }
}

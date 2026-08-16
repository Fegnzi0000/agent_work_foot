package com.hyf.agent_work_foot.slot;

import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.config.SlotProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 单实例Slot固定窗口限流实现；未来多实例可替换为Redis或网关而不修改接口流程。 */
@Component
public class InMemorySlotRateLimiter implements SlotRateLimiter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final SlotProperties properties;
    private final Clock clock;

    /** 作用：创建限流器。输入：外部阈值与UTC时钟。输出：实例。逻辑：不在业务代码硬编码阈值。 */
    public InMemorySlotRateLimiter(SlotProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** 作用：校验用户固定窗口配额。输入：用户ID。输出：允许时无返回。逻辑：同一用户窗口对象内同步计数。 */
    @Override
    public void check(String userId) {
        Instant now = clock.instant();
        Window window = windows.computeIfAbsent(userId, key -> new Window(now, 0));
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

    /** 单个用户的可变固定窗口状态，仅在对象监视器内访问。 */
    private static final class Window {
        private Instant startedAt;
        private int count;

        /** 作用：创建用户窗口状态。输入：窗口起点与初始计数。输出：窗口实例。逻辑：状态只由外层限流器在同步块中修改。 */
        private Window(Instant startedAt, int count) {
            this.startedAt = startedAt;
            this.count = count;
        }
    }
}

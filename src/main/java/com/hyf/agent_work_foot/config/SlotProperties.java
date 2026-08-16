package com.hyf.agent_work_foot.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Slot 模块外部配置，集中承载结果有效期与生成接口限流参数。 */
@ConfigurationProperties(prefix = "app.slot")
public record SlotProperties(Duration spinTtl, RateLimit rateLimit) {
    /** 用户维度固定窗口限流配置。 */
    public record RateLimit(int maxAttempts, Duration window) { }
}

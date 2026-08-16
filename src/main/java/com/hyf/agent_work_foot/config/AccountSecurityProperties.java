package com.hyf.agent_work_foot.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 账号安全模块外部配置，集中承载密码二次验证的限流阈值和窗口。 */
@ConfigurationProperties(prefix = "app.account-security")
public record AccountSecurityProperties(RateLimit rateLimit) {
    /** 用户和IP组合维度的固定窗口限流配置。 */
    public record RateLimit(int maxAttempts, Duration window) {
    }
}

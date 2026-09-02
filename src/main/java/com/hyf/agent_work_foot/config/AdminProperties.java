package com.hyf.agent_work_foot.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Admin模块外部配置，集中管理临时密码期限、限流和受控管理员初始化参数。 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(
        Duration temporaryPasswordTtl,
        RateLimit rateLimit,
        Bootstrap bootstrap
) {
    /** 临时密码生成的两个固定窗口限流维度。 */
    public record RateLimit(
            int targetMaxAttempts,
            Duration targetWindow,
            int adminMaxAttempts,
            Duration adminWindow
    ) {
    }

    /** 一次性ADMIN提升命令配置；默认关闭，不能作为HTTP功能使用。 */
    public record Bootstrap(boolean enabled, String email, String account) {
    }
}

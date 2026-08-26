package com.hyf.agent_work_foot.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 网页客户端跨域白名单；未配置来源时保持空列表，避免默认开放跨域。 */
@ConfigurationProperties(prefix = "app.web.cors")
public record WebCorsProperties(List<String> allowedOrigins) {
    public WebCorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}

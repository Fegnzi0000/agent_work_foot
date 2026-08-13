package com.hyf.agent_work_foot.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * requestId 生命周期 Filter。
 *
 * <p>为每个 HTTP 请求创建唯一标识并写入响应头，必须早于安全与业务 Filter 执行；不处理认证和业务响应。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    /**
     * 作用：建立并清理单个请求的 requestId 上下文。
     *
     * <p>输入：Servlet 请求、响应及后续过滤器链。输出：无直接返回值，响应头包含 X-Request-Id。
     * 逻辑：生成 UUID、绑定 ThreadLocal、继续链路，并在 finally 中清理上下文。</p>
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        RequestIdContext.set(requestId);
        response.setHeader("X-Request-Id", requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestIdContext.clear();
        }
    }
}

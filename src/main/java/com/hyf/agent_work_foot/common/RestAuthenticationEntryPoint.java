package com.hyf.agent_work_foot.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Spring Security 未认证请求的统一 JSON 输出入口。 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;

    /** 作用：注入 JSON 序列化器。输入：ObjectMapper。输出：入口实例。逻辑：保存依赖。 */
    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 作用：输出401错误。输入：请求、响应和认证异常。输出：统一JSON。逻辑：设置状态后写入公共错误模型。 */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        try {
            objectMapper.writeValue(response.getOutputStream(),
                    ErrorResponse.of("AUTH_TOKEN_INVALID", "登录状态无效", List.of()));
        } catch (Exception ignored) {
            // 响应流不可写时保留401状态。
        }
    }
}

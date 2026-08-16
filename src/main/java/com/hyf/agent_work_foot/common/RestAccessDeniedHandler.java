package com.hyf.agent_work_foot.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 已认证用户权限不足时的统一403 JSON输出入口。 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {
    private final ObjectMapper objectMapper;

    /** 作用：注入序列化器。输入：ObjectMapper。输出：Handler实例。逻辑：保存依赖。 */
    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 作用：输出403错误。输入：请求、响应与权限异常。输出：统一JSON。逻辑：不泄露具体权限映射。 */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception) {
        boolean passwordChangeRequired = SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> AppPermissions.PASSWORD_CHANGE_REQUIRED_STATE.equals(authority.getAuthority()));
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        try {
            String code = passwordChangeRequired ? "PASSWORD_CHANGE_REQUIRED" : "FORBIDDEN";
            String message = passwordChangeRequired ? "请先修改密码" : "无权限执行该操作";
            objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(code, message, List.of()));
        } catch (Exception ignored) {
            // 响应流不可写时保留403状态。
        }
    }
}

package com.hyf.agent_work_foot.auth;

import com.hyf.agent_work_foot.common.AppPermissions;
import com.hyf.agent_work_foot.common.RestAccessDeniedHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 强制改密账号的集中访问限制Filter，避免每个业务Controller重复判断mustChangePassword。 */
@Component
public class AccountRestrictionFilter extends OncePerRequestFilter {
    private static final Set<AllowedRequest> ALLOWED = Set.of(
            new AllowedRequest(HttpMethod.GET.name(), "/api/v1/users/me"),
            new AllowedRequest(HttpMethod.POST.name(), "/api/v1/users/me/change-password"),
            new AllowedRequest(HttpMethod.POST.name(), "/api/v1/auth/refresh"),
            new AllowedRequest(HttpMethod.POST.name(), "/api/v1/auth/logout")
    );
    private final RestAccessDeniedHandler accessDeniedHandler;

    /** 作用：注入统一403处理器。输入：安全错误处理器。输出：Filter实例。逻辑：复用公共错误和requestId结构。 */
    public AccountRestrictionFilter(RestAccessDeniedHandler accessDeniedHandler) {
        this.accessDeniedHandler = accessDeniedHandler;
    }

    /**
     * 作用：阻止强制改密账号访问普通业务接口。
     * 输入：HTTP请求、响应和后续Filter链。输出：允许时继续，拒绝时写403。
     * 逻辑：检查Security Context的状态authority，只放行冻结的四个方法与路径组合。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean passwordChangeRequired = authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> AppPermissions.PASSWORD_CHANGE_REQUIRED_STATE.equals(authority.getAuthority()));
        if (passwordChangeRequired
                && !ALLOWED.contains(new AllowedRequest(request.getMethod(), request.getRequestURI()))) {
            accessDeniedHandler.handle(request, response, new AccessDeniedException("PASSWORD_CHANGE_REQUIRED"));
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** 强制改密白名单中的精确HTTP方法与路径。 */
    private record AllowedRequest(String method, String path) {
    }
}

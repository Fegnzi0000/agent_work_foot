package com.hyf.agent_work_foot.auth;

import com.hyf.agent_work_foot.common.AppConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer JWT 认证 Filter。
 *
 * <p>校验 Access Token 后将用户 ID 和角色写入 Security Context；不直接输出错误，由 Spring Security 统一处理未认证请求。</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final RolePermissionResolver permissionResolver;
    private final AccountSecurityService accountSecurityService;

    /** 作用：注入 JWT 服务。输入：Token 签发与校验服务。输出：Filter 实例。逻辑：保存依赖。 */
    public JwtAuthenticationFilter(JwtService jwtService, RolePermissionResolver permissionResolver,
                                   AccountSecurityService accountSecurityService) {
        this.jwtService = jwtService;
        this.permissionResolver = permissionResolver;
        this.accountSecurityService = accountSecurityService;
    }

    /**
     * 作用：提取并验证请求中的 Bearer Access Token。
     *
     * <p>输入：请求、响应和后续 Filter 链。输出：无直接返回值；验证成功时 Security Context 包含当前用户。
     * 逻辑：只处理 Bearer 头，校验失败则清空上下文，继续链路以交给认证入口返回统一错误。</p>
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                JwtService.AccessToken token = jwtService.verifyAccessToken(authorization.substring(7));
                var state = accountSecurityService.accessState(token.userId());
                if (state == null || !AppConstants.USER_STATUS_ACTIVE.equals(state.status())
                        || state.authVersion() != token.authVersion()) {
                    throw new IllegalStateException("Access Token账号状态已失效");
                }
                var authentication = new UsernamePasswordAuthenticationToken(
                        token.userId(),
                        null,
                        permissionResolver.resolve(state.role(), state.mustChangePassword())
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}

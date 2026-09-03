package com.hyf.agent_work_foot.auth;

import com.hyf.agent_work_foot.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证模块的 HTTP 入口。
 *
 * <p>负责请求参数校验、限流调用和响应包装；不处理密码、Token 或数据库规则，这些由 AuthService 负责。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthRateLimiter rateLimiter;

    /** 作用：注入认证用例与可替换限流器。输入：认证服务、限流器。输出：控制器实例。逻辑：保存依赖。 */
    public AuthController(AuthService authService, AuthRateLimiter rateLimiter) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * 作用：注册新用户并创建初始会话。
     *
     * <p>输入：通过 Bean Validation 的注册参数和 HTTP 请求。输出：201 及用户、Access Token、Refresh Token。
     * 逻辑：先按客户端 IP 限流，再交由服务在事务中创建用户和初始化数据。</p>
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponses.AuthData>> register(
            @Valid @RequestBody AuthRequests.RegisterRequest request,
            HttpServletRequest servletRequest
    ) {
        rateLimiter.checkRegistration(servletRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(authService.register(request), "注册成功"));
    }

    /**
     * 作用：验证账号密码并签发会话。
     *
     * <p>输入：登录参数和 HTTP 请求。输出：认证数据或统一认证失败错误。
     * 逻辑：IP 与标准化邮箱共同限流，随后由服务校验账号状态和密码。</p>
     */
    @PostMapping("/login")
    public ApiResponse<AuthResponses.AuthData> login(
            @Valid @RequestBody AuthRequests.LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        rateLimiter.checkLogin(servletRequest.getRemoteAddr(), request.email().trim().toLowerCase(Locale.ROOT));
        return ApiResponse.ok(authService.login(request), "登录成功");
    }

    /** 微信小程序一键登录：提交 wx.login 的一次性 code，成功后复用现有 JWT 会话响应。 */
    @PostMapping("/wechat/mini-program/login")
    public ApiResponse<AuthResponses.AuthData> weChatMiniProgramLogin(
            @Valid @RequestBody AuthRequests.WeChatMiniProgramLoginRequest request,
            HttpServletRequest servletRequest
    ) {
        rateLimiter.checkWeChatLogin(servletRequest.getRemoteAddr());
        return ApiResponse.ok(authService.loginWithWeChatMiniProgram(request.code()), "微信登录成功");
    }

    /** 已有邮箱用户绑定微信身份：不创建第二个业务账号，绑定成功后直接签发原账号会话。 */
    @PostMapping("/wechat/mini-program/bind")
    public ApiResponse<AuthResponses.AuthData> bindWeChatMiniProgram(
            @Valid @RequestBody AuthRequests.BindWeChatMiniProgramRequest request,
            HttpServletRequest servletRequest
    ) {
        rateLimiter.checkLogin(servletRequest.getRemoteAddr(), request.email().trim().toLowerCase(Locale.ROOT));
        rateLimiter.checkWeChatLogin(servletRequest.getRemoteAddr());
        return ApiResponse.ok(authService.bindWeChatMiniProgram(request), "微信绑定成功");
    }

    /**
     * 作用：轮换 Refresh Token 并签发新的 Token 对。
     *
     * <p>输入：Refresh Token 和 HTTP 请求。输出：新的 Access Token、Refresh Token 及有效期。
     * 逻辑：先按 IP 限流，服务在事务中撤销旧 Token 并保存新 Token。</p>
     */
    @PostMapping("/refresh")
    public ApiResponse<AuthResponses.TokenData> refresh(
            @Valid @RequestBody AuthRequests.RefreshTokenRequest request,
            HttpServletRequest servletRequest
    ) {
        rateLimiter.checkRefresh(servletRequest.getRemoteAddr());
        return ApiResponse.ok(authService.refresh(request.refreshToken()), "刷新成功");
    }

    /**
     * 作用：撤销当前 Refresh Token。
     *
     * <p>输入：客户端持有的 Refresh Token。输出：空成功响应或认证失败错误。
     * 逻辑：服务只撤销该 Token，不影响该用户的其他设备会话。</p>
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody AuthRequests.RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.ok(null, "退出成功");
    }
}

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

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthRateLimiter rateLimiter;
    public AuthController(AuthService authService, AuthRateLimiter rateLimiter) { this.authService = authService; this.rateLimiter = rateLimiter; }
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponses.AuthData>> register(@Valid @RequestBody AuthRequests.RegisterRequest request, HttpServletRequest servletRequest) {
        rateLimiter.checkRegistration(servletRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(authService.register(request), "注册成功"));
    }
    @PostMapping("/login")
    public ApiResponse<AuthResponses.AuthData> login(@Valid @RequestBody AuthRequests.LoginRequest request, HttpServletRequest servletRequest) {
        rateLimiter.checkLogin(servletRequest.getRemoteAddr(), request.email().trim().toLowerCase(Locale.ROOT));
        return ApiResponse.ok(authService.login(request), "登录成功");
    }
    @PostMapping("/refresh")
    public ApiResponse<AuthResponses.TokenData> refresh(@Valid @RequestBody AuthRequests.RefreshTokenRequest request, HttpServletRequest servletRequest) {
        rateLimiter.checkRefresh(servletRequest.getRemoteAddr());
        return ApiResponse.ok(authService.refresh(request.refreshToken()), "刷新成功");
    }
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody AuthRequests.RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.ok(null, "退出成功");
    }
}

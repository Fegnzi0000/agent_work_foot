package com.hyf.agent_work_foot.admin;

import com.hyf.agent_work_foot.auth.AuthRateLimiter;
import com.hyf.agent_work_foot.auth.AuthRequests;
import com.hyf.agent_work_foot.auth.AuthResponses;
import com.hyf.agent_work_foot.auth.AuthService;
import com.hyf.agent_work_foot.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员网页登录入口；与小程序邮箱登录分离，只接受管理员账号名。 */
@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {
    private final AuthService authService;
    private final AuthRateLimiter rateLimiter;

    public AdminAuthController(AuthService authService, AuthRateLimiter rateLimiter) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    /** 作用：用管理员账号和密码创建会话。输入：账号、密码、客户端IP。输出：认证数据。逻辑：限流后仅按管理员账号查询。 */
    @PostMapping("/login")
    public ApiResponse<AuthResponses.AuthData> login(
            @Valid @RequestBody AuthRequests.AdminLoginRequest request,
            HttpServletRequest servletRequest
    ) {
        rateLimiter.checkLogin(servletRequest.getRemoteAddr(), request.account().trim().toLowerCase(Locale.ROOT));
        return ApiResponse.ok(authService.loginAdmin(request), "登录成功");
    }
}

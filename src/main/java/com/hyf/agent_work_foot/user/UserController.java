package com.hyf.agent_work_foot.user;

import com.hyf.agent_work_foot.common.ApiResponse;
import com.hyf.agent_work_foot.auth.AccountSecurityRateLimiter;
import com.hyf.agent_work_foot.preference.PreferenceRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录用户资料与引导的 HTTP 入口。
 *
 * <p>用户 ID 始终取自 JWT 安全上下文，不接受前端提交的归属用户 ID；具体业务交给 UserService。</p>
 */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {
    private final UserService userService;
    private final AccountSecurityRateLimiter accountSecurityRateLimiter;

    /** 作用：注入用户服务。输入：用户业务服务。输出：控制器实例。逻辑：保存依赖。 */
    public UserController(UserService userService, AccountSecurityRateLimiter accountSecurityRateLimiter) {
        this.userService = userService;
        this.accountSecurityRateLimiter = accountSecurityRateLimiter;
    }

    /**
     * 作用：获取当前认证用户资料。
     *
     * <p>输入：Spring Security 注入的认证主体。输出：当前用户公开资料。
     * 逻辑：只使用认证主体名称作为用户 ID，避免客户端越权读取其他用户。</p>
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ACCOUNT_SELF_VIEW')")
    public ApiResponse<UserResponses.UserData> current(Authentication authentication) {
        return ApiResponse.ok(userService.currentUser(authentication.getName()), "获取成功");
    }

    /**
     * 作用：修改当前用户昵称。
     *
     * <p>输入：校验通过的昵称和认证主体。输出：更新后的用户资料。
     * 逻辑：用户归属由 JWT 决定，服务仅更新该用户的昵称。</p>
     */
    @PatchMapping("/profile")
    public ApiResponse<UserResponses.UserData> profile(
            @Valid @RequestBody UserRequests.ProfilePatchRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(userService.updateProfile(authentication.getName(), request), "修改成功");
    }

    /**
     * 作用：提交当前用户首次引导资料。
     *
     * <p>输入：引导偏好与预算请求、认证主体。输出：已完成引导的用户资料。
     * 逻辑：服务将偏好、预算、可选昵称和引导状态放在同一事务中完成。</p>
     */
    @PutMapping("/onboarding")
    public ResponseEntity<ApiResponse<UserResponses.UserData>> onboarding(
            @Valid @RequestBody PreferenceRequests.OnboardingRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                userService.completeOnboarding(authentication.getName(), request),
                "引导已完成"
        ));
    }

    /**
     * 作用：修改当前账号密码并撤销全部旧会话。
     * 输入：严格密码请求、认证主体和客户端IP。输出：200空成功响应。
     * 逻辑：先消费用户加IP限流配额，再由用户服务委托认证模块完成安全事务。
     */
    @PostMapping("/change-password")
    @PreAuthorize("hasAuthority('ACCOUNT_CHANGE_PASSWORD')")
    public ApiResponse<Void> changePassword(@Valid @RequestBody UserRequests.ChangePasswordRequest request,
                                            Authentication authentication, HttpServletRequest servletRequest) {
        accountSecurityRateLimiter.check(authentication.getName(), servletRequest.getRemoteAddr());
        userService.changePassword(authentication.getName(), request);
        return ApiResponse.ok(null, "密码修改成功，请重新登录");
    }

    /**
     * 作用：软注销当前普通用户并撤销全部会话。
     * 输入：密码与CANCEL确认、认证主体和客户端IP。输出：200空成功响应。
     * 逻辑：ACCOUNT_CANCEL排除管理员，密码二次验证与改密共享限流窗口。
     */
    @PostMapping("/cancel")
    @PreAuthorize("hasAuthority('ACCOUNT_CANCEL')")
    public ApiResponse<Void> cancelAccount(@Valid @RequestBody UserRequests.CancelAccountRequest request,
                                           Authentication authentication, HttpServletRequest servletRequest) {
        accountSecurityRateLimiter.check(authentication.getName(), servletRequest.getRemoteAddr());
        userService.cancelAccount(authentication.getName(), request);
        return ApiResponse.ok(null, "账号已注销");
    }
}

package com.hyf.agent_work_foot.user;

import com.hyf.agent_work_foot.common.ApiResponse;
import com.hyf.agent_work_foot.preference.PreferenceRequests;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    /** 作用：注入用户服务。输入：用户业务服务。输出：控制器实例。逻辑：保存依赖。 */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 作用：获取当前认证用户资料。
     *
     * <p>输入：Spring Security 注入的认证主体。输出：当前用户公开资料。
     * 逻辑：只使用认证主体名称作为用户 ID，避免客户端越权读取其他用户。</p>
     */
    @GetMapping
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
}

package com.hyf.agent_work_foot.auth;
import com.hyf.agent_work_foot.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class WeChatAccountController {
    private final AccountSecurityService service;
    private final AccountSecurityRateLimiter limiter;
    public WeChatAccountController(AccountSecurityService service, AccountSecurityRateLimiter limiter) { this.service=service; this.limiter=limiter; }
    @PostMapping("/api/v1/users/me/cancel-wechat")
    @PreAuthorize("hasRole('USER') and hasAuthority('ACCOUNT_CANCEL')")
    public ApiResponse<Void> cancel(Authentication authentication, @Valid @RequestBody CancelRequest body, HttpServletRequest request) {
        limiter.check(authentication.getName(), request.getRemoteAddr());
        service.cancelWithWeChat(authentication.getName(), body.code(), body.confirmation());
        return ApiResponse.ok(null,"账号已注销，数据请求或重新注册请联系开发者");
    }
    public record CancelRequest(@NotBlank @Size(max=1024) String code, @NotBlank @Pattern(regexp="CANCEL") String confirmation) { }
}

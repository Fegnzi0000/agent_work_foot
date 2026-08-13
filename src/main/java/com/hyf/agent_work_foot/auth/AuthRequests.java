package com.hyf.agent_work_foot.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 认证接口的请求 DTO 集合。
 *
 * <p>仅描述外部 HTTP 输入与字段校验规则；不执行注册、登录或 Token 校验业务逻辑。</p>
 */
public final class AuthRequests {
    /** 作用：禁止创建 DTO 容器实例。输入：无。输出：无。逻辑：仅通过嵌套 record 使用。 */
    private AuthRequests() {
    }

    /** 注册请求：邮箱、密码和确认密码；密码限制为 6 至 20 位字母、数字或下划线。 */
    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{6,20}$") String password,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{6,20}$") String confirmPassword
    ) {
    }

    /** 登录请求：邮箱和密码；字段格式由 Controller 入参校验。 */
    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{6,20}$") String password
    ) {
    }

    /** 刷新或退出请求：携带非空的 Refresh Token 原文。 */
    public record RefreshTokenRequest(@NotBlank String refreshToken) {
    }
}

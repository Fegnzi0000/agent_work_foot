package com.hyf.agent_work_foot.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 用户模块的请求 DTO 集合。
 *
 * <p>描述用户资料接口可接受的字段及格式，不负责当前用户身份和持久化。</p>
 */
public final class UserRequests {
    /** 作用：禁止创建 DTO 容器实例。输入：无。输出：无。逻辑：仅通过嵌套 record 使用。 */
    private UserRequests() {
    }

    /** 资料更新请求：昵称不能为空，最大长度为 20。 */
    public record ProfilePatchRequest(@NotBlank @Size(max = 20) String nickname) {
    }

    /** 修改密码请求：三个密码均不做trim，并严格使用项目统一密码字符规则。 */
    public record ChangePasswordRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{6,20}$") String currentPassword,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{6,20}$") String newPassword,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{6,20}$") String confirmNewPassword
    ) {
    }

    /** 账号注销请求：重新验证当前密码并要求严格大写CANCEL确认。 */
    public record CancelAccountRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{6,20}$") String currentPassword,
            @NotBlank @Pattern(regexp = "^CANCEL$") String confirmation
    ) {
    }
}

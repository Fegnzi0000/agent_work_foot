package com.hyf.agent_work_foot.user;

/**
 * 用户模块的响应 DTO 集合。
 *
 * <p>仅输出当前用户可见资料，不包含密码哈希、Refresh Token 或其他用户的数据。</p>
 */
public final class UserResponses {
    /** 作用：禁止创建 DTO 容器实例。输入：无。输出：无。逻辑：仅通过嵌套 record 使用。 */
    private UserResponses() {
    }

    /** 当前用户可公开的资料和账号状态。 */
    public record UserData(
            String id,
            String email,
            String nickname,
            String avatarUrl,
            String role,
            String status,
            boolean onboardingCompleted,
            boolean mustChangePassword
    ) {
    }
}

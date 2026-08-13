package com.hyf.agent_work_foot.auth;

/**
 * 认证接口的响应 DTO 集合。
 *
 * <p>只定义 HTTP 输出结构；字段由 AuthService 填充，不包含密码哈希或 Refresh Token 摘要等内部数据。</p>
 */
public final class AuthResponses {
    /** 作用：禁止创建 DTO 容器实例。输入：无。输出：无。逻辑：仅通过嵌套 record 使用。 */
    private AuthResponses() {
    }

    /** 已认证用户的可公开资料，不包含密码与内部 Token 信息。 */
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

    /** 注册和登录成功后的完整认证数据，包含用户资料、双 Token 与下一步页面提示。 */
    public record AuthData(
            String accessToken,
            long accessTokenExpiresIn,
            String refreshToken,
            long refreshTokenExpiresIn,
            UserData user,
            String nextStep
    ) {
    }

    /** 刷新 Token 成功后的 Token 对，不重复输出用户资料。 */
    public record TokenData(
            String accessToken,
            long accessTokenExpiresIn,
            String refreshToken,
            long refreshTokenExpiresIn
    ) {
    }
}

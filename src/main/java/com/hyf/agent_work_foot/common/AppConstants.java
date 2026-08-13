package com.hyf.agent_work_foot.common;

/**
 * 跨模块共享的稳定业务常量。
 *
 * <p>仅放置角色、状态和枚举值等不会随环境变化的值；密钥、时长和阈值仍应放在外部配置中。</p>
 */
public final class AppConstants {
    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String USER_STATUS_ACTIVE = "ACTIVE";
    public static final String TOKEN_REVOKE_ROTATED = "ROTATED";
    public static final String TOKEN_REVOKE_LOGOUT = "LOGOUT";
    public static final String PREFERENCE_PRESET = "PRESET";
    public static final String PREFERENCE_CUSTOM = "CUSTOM";

    /** 作用：禁止创建常量类实例。输入：无。输出：无。逻辑：常量只通过类名访问。 */
    private AppConstants() {
    }
}

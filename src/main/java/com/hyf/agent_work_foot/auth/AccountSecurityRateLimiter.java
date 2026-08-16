package com.hyf.agent_work_foot.auth;

/** 修改密码与注销密码二次验证的可替换用户加IP限流边界。 */
public interface AccountSecurityRateLimiter {
    /** 作用：消费一次账号安全操作配额。输入：认证用户ID和客户端IP。输出：允许时无返回。逻辑：超限抛统一RATE_LIMITED。 */
    void check(String userId, String ip);
}

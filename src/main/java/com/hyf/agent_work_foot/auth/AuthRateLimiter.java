package com.hyf.agent_work_foot.auth;

/**
 * 认证接口限流抽象。
 *
 * <p>Controller 仅依赖本接口，当前由内存实现提供，后续可以替换 Redis 或网关实现而不修改认证流程。</p>
 */
public interface AuthRateLimiter {
    /** 作用：检查登录频率。输入：客户端 IP 与标准化邮箱。输出：超限时抛异常，否则无返回。逻辑：按 IP 和邮箱组合限流。 */
    void checkLogin(String clientIp, String email);

    /** 作用：检查微信小程序登录频率。输入：客户端 IP。输出：超限时抛异常。逻辑：与邮箱密码登录使用独立限流键。 */
    void checkWeChatLogin(String clientIp);

    /** 作用：检查注册频率。输入：客户端 IP。输出：超限时抛异常，否则无返回。逻辑：按 IP 限流。 */
    void checkRegistration(String clientIp);

    /** 作用：检查刷新 Token 频率。输入：客户端 IP。输出：超限时抛异常，否则无返回。逻辑：按 IP 限流。 */
    void checkRefresh(String clientIp);
}

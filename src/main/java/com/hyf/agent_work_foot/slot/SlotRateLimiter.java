package com.hyf.agent_work_foot.slot;

/** Slot生成接口的可替换用户维度限流边界。 */
public interface SlotRateLimiter {
    /** 作用：消费一次生成配额。输入：认证用户ID。输出：允许时无返回。逻辑：超限抛统一RATE_LIMITED。 */
    void check(String userId);
}

package com.hyf.agent_work_foot.admin;

/** 临时密码生成限流抽象；当前为单实例内存实现，未来可替换共享限流而不修改AdminService。 */
public interface AdminRateLimiter {
    /** 作用：检查管理员总量和目标用户两个窗口。输入：管理员、目标用户和来源IP。输出：允许时无返回，超限抛429。逻辑：实现必须同时计数两个维度。 */
    void checkTemporaryPassword(String adminUserId, String targetUserId, String ipAddress);
}

package com.hyf.agent_work_foot.auth;

public interface AuthRateLimiter {
    void checkLogin(String clientIp, String email);
    void checkRegistration(String clientIp);
    void checkRefresh(String clientIp);
}

package com.hyf.agent_work_foot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 微信小程序登录的服务端私密配置。AppSecret 仅由后端读取，绝不下发至小程序。 */
@ConfigurationProperties(prefix = "app.wechat.mini-program")
public record WeChatMiniProgramProperties(String appId, String appSecret, String code2SessionUrl) {
    /** 作用：判断当前环境是否已配置可用的微信登录凭据。输入：无。输出：是否可调用微信。逻辑：避免未配置时发出无效外部请求。 */
    public boolean configured() {
        return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
    }
}

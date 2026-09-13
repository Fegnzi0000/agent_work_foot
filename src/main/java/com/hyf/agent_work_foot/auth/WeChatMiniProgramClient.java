package com.hyf.agent_work_foot.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.config.WeChatMiniProgramProperties;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/** 微信小程序 code2Session 的唯一后端调用入口，不向上层暴露 AppSecret 或 session_key。 */
@Component
public class WeChatMiniProgramClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(WeChatMiniProgramClient.class);

    private final RestClient restClient;
    private final WeChatMiniProgramProperties properties;
    private final ObjectMapper objectMapper;

    /** 作用：注入微信配置。输入：配置。输出：客户端实例。逻辑：使用 Spring 内置无状态 HTTP 客户端，不依赖额外自动配置。 */
    public WeChatMiniProgramClient(WeChatMiniProgramProperties properties, ObjectMapper objectMapper) {
        var httpClient = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(2)).build();
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(java.time.Duration.ofSeconds(3));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 作用：用小程序一次性 code 换取微信身份。
     * 输入：wx.login 返回的 code。输出：openid 与可选 unionid。
     * 逻辑：仅后端携带 AppSecret 调微信；session_key 不返回、不持久化、不写日志。
     */
    public WeChatIdentity exchangeCode(String code) {
        if (!properties.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WECHAT_LOGIN_UNAVAILABLE", "微信登录暂未配置");
        }
        try {
            URI uri = UriComponentsBuilder.fromUriString(properties.code2SessionUrl())
                    .queryParam("appid", properties.appId())
                    .queryParam("secret", properties.appSecret())
                    .queryParam("js_code", code)
                    .queryParam("grant_type", "authorization_code")
                    .build()
                    .encode()
                    .toUri();
            // 微信接口在不同网络链路上可能携带非标准 JSON Content-Type；先按文本读取，再显式解析，避免响应头差异阻断登录。
            String body = restClient.get().uri(uri).retrieve().body(String.class);
            Code2SessionResponse response = objectMapper.readValue(body, Code2SessionResponse.class);
            if (response == null || response.openId() == null || response.openId().isBlank()) {
                int errorCode = response == null || response.errorCode() == null ? -1 : response.errorCode();
                LOGGER.warn("[微信登录] code2Session 失败 errcode={}", errorCode);
                throw new ApiException(HttpStatus.UNAUTHORIZED, "WECHAT_LOGIN_FAILED", "微信登录凭证无效或已过期");
            }
            return new WeChatIdentity(response.openId(), response.unionId());
        } catch (ApiException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            LOGGER.warn("[微信登录] code2Session 返回内容无法解析 type={}", exception.getClass().getSimpleName());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "WECHAT_LOGIN_UNAVAILABLE", "微信登录服务暂时不可用");
        } catch (RestClientException exception) {
            LOGGER.warn("[微信登录] code2Session 网络调用失败 endpointHost={} causeChain={}", endpointHost(), diagnosticCauseChain(exception, code));
            throw new ApiException(HttpStatus.BAD_GATEWAY, "WECHAT_LOGIN_UNAVAILABLE", "微信登录服务暂时不可用");
        }
    }

    /**
     * 作用：输出排障所需的异常链，同时移除 URL 查询参数中的 AppSecret 与一次性登录 code。
     * 输入：网络异常和本次 code。输出：可安全写入生产日志的文本。
     */
    private String diagnosticCauseChain(Throwable exception, String code) {
        StringBuilder result = new StringBuilder();
        Throwable current = exception;
        while (current != null) {
            if (!result.isEmpty()) {
                result.append(" <- ");
            }
            result.append(current.getClass().getSimpleName());
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                result.append(": ").append(redact(message, code));
            }
            current = current.getCause();
        }
        return result.toString();
    }

    /** 返回固定的目标主机，日志不输出完整请求 URL，避免查询参数进入日志。 */
    private String endpointHost() {
        try {
            URI endpoint = URI.create(properties.code2SessionUrl());
            return endpoint.getHost() == null ? "unknown" : endpoint.getHost();
        } catch (IllegalArgumentException exception) {
            return "invalid-config";
        }
    }

    /** 仅用于日志脱敏；不改变真实请求。 */
    private String redact(String value, String code) {
        return value
                .replace(properties.appSecret(), "***")
                .replace(code, "***")
                .replaceAll("(?i)(secret|js_code)=([^&\\s]+)", "$1=***");
    }

    /** 微信侧已验证的业务身份；不携带 session_key，避免其离开本类。 */
    public record WeChatIdentity(String openId, String unionId) {
    }

    /** 微信 code2Session 原始响应，仅在本类内部使用。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Code2SessionResponse(
            @JsonProperty("openid") String openId,
            @JsonProperty("unionid") String unionId,
            @JsonProperty("errcode") Integer errorCode
    ) {
    }
}

package com.hyf.agent_work_foot.common;

import java.time.Instant;

/**
 * 成功接口的统一响应模型。
 *
 * <p>Controller 使用本类型返回业务数据和当前请求标识；错误响应由异常处理链路单独输出。</p>
 */
public record ApiResponse<T>(String code, String message, T data, String requestId, Instant timestamp) {
    /**
     * 作用：创建标准成功响应。
     *
     * <p>输入：data 为可为空的业务结果；message 为成功提示。输出：code 固定为 OK 的响应。
     * 逻辑：读取当前 requestId 并记录创建时间，使成功响应可追踪。</p>
     */
    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>("OK", message, data, RequestIdContext.current(), Instant.now());
    }
}

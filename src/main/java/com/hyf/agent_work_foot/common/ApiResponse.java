package com.hyf.agent_work_foot.common;

import java.time.Instant;

public record ApiResponse<T>(String code, String message, T data, String requestId, Instant timestamp) {
    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>("OK", message, data, RequestIdContext.current(), Instant.now());
    }
}

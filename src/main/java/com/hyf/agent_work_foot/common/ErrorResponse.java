package com.hyf.agent_work_foot.common;

import java.time.Instant;
import java.util.List;

/**
 * 全链路统一错误响应。
 *
 * <p>业务异常、参数校验以及 Spring Security 的 401/403 都使用本结构，确保 requestId 和详情契约一致。</p>
 */
public record ErrorResponse(
        String code,
        String message,
        List<FieldErrorDetail> details,
        String requestId,
        Instant timestamp
) {
    /**
     * 作用：创建当前请求的错误响应。
     * 输入：错误码、说明和字段详情。输出：带 requestId 和当前时间的错误对象。
     * 逻辑：详情复制为不可变列表，避免响应生成后被修改。
     */
    public static ErrorResponse of(String code, String message, List<FieldErrorDetail> details) {
        return new ErrorResponse(code, message, List.copyOf(details), RequestIdContext.current(), Instant.now());
    }
}

package com.hyf.agent_work_foot.common;

import org.springframework.http.HttpStatus;
import java.util.List;

/**
 * 统一业务异常。
 *
 * <p>供业务和安全层表达可预期失败，由 {@link ApiExceptionHandler} 转为 API 错误响应；不负责日志与响应输出。</p>
 */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final List<FieldErrorDetail> details;

    /**
     * 作用：创建带 HTTP 状态和稳定错误码的异常。
     *
     * <p>输入：status 为响应状态；code 为业务错误码；message 为调用方可见说明。输出：异常实例。
     * 逻辑：保存状态和编码，并将说明传给父类。</p>
     */
    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    /**
     * 作用：创建带字段详情的业务异常。
     * 输入：HTTP 状态、错误码、说明和稳定排序的字段详情。输出：异常实例。
     * 逻辑：复制详情列表，避免调用方后续修改错误响应内容。
     */
    public ApiException(HttpStatus status, String code, String message, List<FieldErrorDetail> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = List.copyOf(details);
    }

    /** 作用：返回 HTTP 状态。输入：无。输出：构造时保存的状态。逻辑：直接读取字段。 */
    public HttpStatus status() {
        return status;
    }

    /** 作用：返回稳定错误码。输入：无。输出：构造时保存的编码。逻辑：直接读取字段。 */
    public String code() {
        return code;
    }

    /** 作用：返回字段错误详情。输入：无。输出：不可变详情列表。逻辑：直接读取构造时保存的列表。 */
    public List<FieldErrorDetail> details() {
        return details;
    }
}

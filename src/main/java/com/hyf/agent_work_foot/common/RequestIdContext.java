package com.hyf.agent_work_foot.common;

import java.util.UUID;

/**
 * 当前 HTTP 请求的 requestId 线程上下文。
 *
 * <p>由 {@link RequestIdFilter} 写入和清理，响应与异常处理层只读取；不负责异步线程间传递。</p>
 */
public final class RequestIdContext {
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    /** 作用：禁止创建工具类实例。输入：无。输出：无。逻辑：本类只提供静态方法。 */
    private RequestIdContext() {
    }

    /** 作用：绑定当前线程的请求标识。输入：Filter 生成的 requestId。输出：无。逻辑：写入 ThreadLocal。 */
    public static void set(String requestId) {
        REQUEST_ID.set(requestId);
    }

    /** 作用：读取当前请求标识。输入：无。输出：requestId，非请求链路时新建 UUID。逻辑：优先读取 ThreadLocal。 */
    public static String current() {
        return REQUEST_ID.get() == null ? UUID.randomUUID().toString() : REQUEST_ID.get();
    }

    /** 作用：清理当前线程的请求标识。输入：无。输出：无。逻辑：移除 ThreadLocal，避免线程复用串号。 */
    public static void clear() {
        REQUEST_ID.remove();
    }
}

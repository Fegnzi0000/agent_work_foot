package com.hyf.agent_work_foot.common;

import java.util.UUID;

public final class RequestIdContext {
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();
    private RequestIdContext() { }

    public static void set(String requestId) { REQUEST_ID.set(requestId); }
    public static String current() { return REQUEST_ID.get() == null ? UUID.randomUUID().toString() : REQUEST_ID.get(); }
    public static void clear() { REQUEST_ID.remove(); }
}

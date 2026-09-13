package com.hyf.agent_work_foot.common;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证云托管探针控制器始终返回成功响应。 */
class CloudHostingProbeControllerTest {
    @Test
    void returnsOk() {
        assertEquals(200, new CloudHostingProbeController().probe().getStatusCode().value());
    }
}

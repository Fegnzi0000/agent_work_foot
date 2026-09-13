package com.hyf.agent_work_foot.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 微信云托管存活探针，不依赖数据库、Redis、JWT 或业务接口。 */
@RestController
public class CloudHostingProbeController {
    /**
     * 作用：响应微信云托管的容器存活探测。
     * 输入：无。输出：固定 HTTP 200。逻辑：只证明 Web 进程已可接受请求，避免鉴权拦截探针。
     */
    @GetMapping("/__tcb_probe__")
    public ResponseEntity<Void> probe() {
        return ResponseEntity.ok().build();
    }
}

package com.hyf.agent_work_foot.admin;

import com.hyf.agent_work_foot.common.ApiResponse;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理员Dashboard HTTP入口，只返回聚合数据，不暴露任何单个用户的饮食或偏好。 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminDashboardController {
    private final AdminDashboardService service;

    public AdminDashboardController(AdminDashboardService service) {
        this.service = service;
    }

    /** 获取Dashboard；日期必须同时传递，未传时使用含今天在内的最近7天。 */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminDashboardResponses.DashboardData> dashboard(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        return ApiResponse.ok(service.dashboard(startDate, endDate), "获取成功");
    }
}

package com.hyf.agent_work_foot.admin;

import com.hyf.agent_work_foot.common.ApiResponse;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理员审计日志HTTP入口；仅提供读取、筛选与分页，不提供修改、删除或导出。 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminAuditLogController {
    private final AdminAuditLogService service;

    public AdminAuditLogController(AdminAuditLogService service) {
        this.service = service;
    }

    /** 查询管理员操作审计；日期按上海自然日理解，省略时默认最近30天。 */
    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminResponses.AdminAuditLogPageData> list(
            @RequestParam(required = false) String adminAccount,
            @RequestParam(required = false) String targetUserEmail,
            @RequestParam(required = false) String targetUserNickname,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(service.list(adminAccount, targetUserEmail, targetUserNickname, action, result, startDate, endDate, page, size),
                "获取成功");
    }
}

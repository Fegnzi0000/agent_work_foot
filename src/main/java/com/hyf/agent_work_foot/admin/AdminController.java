package com.hyf.agent_work_foot.admin;

import com.hyf.agent_work_foot.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 独立管理员界面的后端HTTP入口，只负责参数、权限、状态码与响应包装。 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminController {
    private final AdminService service;

    /** 作用：注入AdminService。输入：管理员应用服务。输出：Controller实例。逻辑：不直接访问Mapper。 */
    public AdminController(AdminService service) {
        this.service = service;
    }

    /** 作用：分页查询可管理账号。输入：认证主体和筛选。输出：200分页响应。逻辑：方法权限要求ADMIN_USER_LIST。 */
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN_USER_LIST')")
    public ApiResponse<AdminResponses.AdminUserPageData> list(
            Authentication authentication,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(
                service.list(authentication.getName(), email, status, page, size), "获取成功"
        );
    }

    /** 作用：启用或禁用账号。输入：目标UUID、严格请求和操作者。输出：200最终账号。逻辑：额外目标权限由Service校验。 */
    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('ADMIN_USER_STATUS_UPDATE')")
    public ApiResponse<AdminResponses.AdminUserData> updateStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminRequests.StatusPatchRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(
                service.updateStatus(authentication.getName(), userId.toString(), request.status()), "修改成功"
        );
    }

    /** 作用：生成一次性临时密码。输入：目标UUID、操作者和来源IP。输出：201且明文只在本响应出现。逻辑：请求不接受正文。 */
    @PostMapping("/{userId}/temporary-password")
    @PreAuthorize("hasAuthority('ADMIN_TEMP_PASSWORD_CREATE')")
    public ResponseEntity<ApiResponse<AdminResponses.TemporaryPasswordData>> createTemporaryPassword(
            @PathVariable UUID userId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                service.createTemporaryPassword(
                        authentication.getName(), userId.toString(), request.getRemoteAddr()
                ),
                "创建成功"
        ));
    }
}

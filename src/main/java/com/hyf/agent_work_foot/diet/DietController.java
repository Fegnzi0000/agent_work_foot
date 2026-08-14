package com.hyf.agent_work_foot.diet;

import com.hyf.agent_work_foot.common.ApiResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 当前用户饮食记录的HTTP入口，只负责鉴权、参数接收和统一响应包装。 */
@RestController
@RequestMapping("/api/v1/diet-records")
public class DietController {
    private final DietService service;

    /** 作用：注入Diet应用服务。输入：服务实例。输出：Controller实例。逻辑：保存唯一业务依赖。 */
    public DietController(DietService service) { this.service = service; }

    /** 作用：分页查询饮食历史。输入：当前用户、筛选和分页。输出：200快照分页。逻辑：权限由DIET_LIST控制。 */
    @GetMapping
    @PreAuthorize("hasAuthority('DIET_LIST')")
    public ApiResponse<DietResponses.DietRecordPageData> list(Authentication authentication,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) LocalDate startDate, @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String mealType, @RequestParam(required = false) String category,
            @RequestParam(required = false) String source) {
        return ApiResponse.ok(service.list(authentication.getName(), page, size, startDate, endDate, mealType, category, source), "获取成功");
    }

    /** 作用：创建手工饮食记录。输入：二选一食物请求和当前用户。输出：201记录快照。逻辑：权限由DIET_CREATE控制。 */
    @PostMapping
    @PreAuthorize("hasAuthority('DIET_CREATE')")
    public ResponseEntity<ApiResponse<DietResponses.DietRecordData>> create(@RequestBody DietRequests.CreateRequest request,
                                                                              Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(authentication.getName(), request), "创建成功"));
    }

    /** 作用：读取统计。输入：当前用户、必填范围和分组。输出：200聚合响应。逻辑：权限由DIET_STATISTICS控制。 */
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('DIET_STATISTICS')")
    public ApiResponse<DietResponses.DietStatisticsData> statistics(Authentication authentication,
            @RequestParam LocalDate startDate, @RequestParam LocalDate endDate, @RequestParam String groupBy) {
        return ApiResponse.ok(service.statistics(authentication.getName(), startDate, endDate, groupBy), "获取成功");
    }

    /** 作用：部分修改饮食记录。输入：记录UUID、PATCH三态请求和当前用户。输出：200最终快照。逻辑：权限由DIET_UPDATE控制。 */
    @PatchMapping("/{dietRecordId}")
    @PreAuthorize("hasAuthority('DIET_UPDATE')")
    public ApiResponse<DietResponses.DietRecordData> patch(@PathVariable UUID dietRecordId,
            @RequestBody DietRequests.PatchRequest request, Authentication authentication) {
        return ApiResponse.ok(service.patch(authentication.getName(), dietRecordId.toString(), request), "修改成功");
    }

    /** 作用：软删除饮食记录。输入：记录UUID和当前用户。输出：204空响应。逻辑：权限由DIET_DELETE控制。 */
    @DeleteMapping("/{dietRecordId}")
    @PreAuthorize("hasAuthority('DIET_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID dietRecordId, Authentication authentication) {
        service.delete(authentication.getName(), dietRecordId.toString());
        return ResponseEntity.noContent().build();
    }
}

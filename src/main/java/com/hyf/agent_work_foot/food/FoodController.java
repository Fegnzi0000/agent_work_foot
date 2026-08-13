package com.hyf.agent_work_foot.food;

import com.hyf.agent_work_foot.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
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

/** 当前用户食物池的五个HTTP入口，负责参数、权限和响应状态，不承载业务规则。 */
@RestController
@RequestMapping("/api/v1/food-options")
public class FoodController {
    private final FoodService service;

    /** 作用：注入FoodService。输入：业务服务。输出：Controller实例。逻辑：保存依赖。 */
    public FoodController(FoodService service) { this.service = service; }

    /** 作用：分页查询食物池。输入：认证主体与筛选参数。输出：200分页响应。逻辑：权限注解校验FOOD_LIST。 */
    @GetMapping
    @PreAuthorize("hasAuthority('FOOD_LIST')")
    public ApiResponse<FoodResponses.FoodPageData> list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<String> tags
    ) {
        return ApiResponse.ok(service.list(authentication.getName(), page, size, keyword, category, tags), "获取成功");
    }

    /** 作用：创建食物。输入：完整请求和认证主体。输出：201最终食物。逻辑：权限注解校验FOOD_CREATE。 */
    @PostMapping
    @PreAuthorize("hasAuthority('FOOD_CREATE')")
    public ResponseEntity<ApiResponse<FoodResponses.FoodOptionData>> create(
            @Valid @RequestBody FoodRequests.FoodWriteRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(authentication.getName(), request), "创建成功"));
    }

    /** 作用：读取食物详情。输入：UUID和认证主体。输出：200最终食物。逻辑：权限和用户归属双重校验。 */
    @GetMapping("/{foodOptionId}")
    @PreAuthorize("hasAuthority('FOOD_VIEW')")
    public ApiResponse<FoodResponses.FoodOptionData> get(@PathVariable UUID foodOptionId, Authentication authentication) {
        return ApiResponse.ok(service.get(authentication.getName(), foodOptionId.toString()), "获取成功");
    }

    /** 作用：部分修改食物。输入：UUID、PATCH请求和认证主体。输出：200最终食物。逻辑：权限注解校验FOOD_UPDATE。 */
    @PatchMapping("/{foodOptionId}")
    @PreAuthorize("hasAuthority('FOOD_UPDATE')")
    public ApiResponse<FoodResponses.FoodOptionData> patch(
            @PathVariable UUID foodOptionId,
            @RequestBody FoodRequests.FoodPatchRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(service.patch(authentication.getName(), foodOptionId.toString(), request), "修改成功");
    }

    /** 作用：软删除食物。输入：UUID和认证主体。输出：204空响应。逻辑：权限注解校验FOOD_DELETE。 */
    @DeleteMapping("/{foodOptionId}")
    @PreAuthorize("hasAuthority('FOOD_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID foodOptionId, Authentication authentication) {
        service.delete(authentication.getName(), foodOptionId.toString());
        return ResponseEntity.noContent().build();
    }
}

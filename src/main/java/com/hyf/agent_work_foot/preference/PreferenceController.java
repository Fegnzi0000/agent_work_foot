package com.hyf.agent_work_foot.preference;

import com.hyf.agent_work_foot.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 偏好预设、当前用户偏好和预算的 HTTP 入口。
 *
 * <p>预设选项可以公开读取；用户偏好接口从 Security Context 提取用户 ID，不接受客户端指定归属。</p>
 */
@RestController
public class PreferenceController {
    private final PreferenceService service;

    /** 作用：注入偏好业务服务。输入：PreferenceService。输出：控制器实例。逻辑：保存依赖。 */
    public PreferenceController(PreferenceService service) {
        this.service = service;
    }

    /**
     * 作用：读取系统提供的有效偏好预设。
     *
     * <p>输入：无。输出：按偏好分类分组的预设项。逻辑：不读取或暴露任何用户私有偏好。</p>
     */
    @GetMapping("/api/v1/preferences/options")
    public ApiResponse<PreferenceResponses.OptionsData> options() {
        return ApiResponse.ok(service.options(), "获取成功");
    }

    /**
     * 作用：读取当前用户的偏好和当前生效预算。
     *
     * <p>输入：认证主体。输出：当前用户的偏好、预算状态和每日预算。逻辑：只以 JWT 主体名称作为归属用户 ID。</p>
     */
    @GetMapping("/api/v1/users/me/preferences")
    public ApiResponse<PreferenceResponses.PreferencesData> get(Authentication authentication) {
        return ApiResponse.ok(service.get(authentication.getName()), "获取成功");
    }

    /**
     * 作用：部分更新当前用户偏好或预算。
     *
     * <p>输入：校验后的补丁请求和认证主体。输出：更新后的完整偏好数据。
     * 逻辑：服务只替换请求中出现的分类，并在事务内维护预算历史。</p>
     */
    @PatchMapping("/api/v1/users/me/preferences")
    public ApiResponse<PreferenceResponses.PreferencesData> patch(
            @Valid @RequestBody PreferenceRequests.PreferencesPatchRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(service.patch(authentication.getName(), request), "修改成功");
    }
}

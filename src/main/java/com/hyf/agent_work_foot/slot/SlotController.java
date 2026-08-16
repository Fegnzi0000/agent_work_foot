package com.hyf.agent_work_foot.slot;

import com.hyf.agent_work_foot.common.ApiResponse;
import com.hyf.agent_work_foot.diet.DietResponses;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Slot的生成与确认HTTP入口，负责可选请求体、路径UUID、权限和统一响应包装。 */
@RestController
@RequestMapping("/api/v1/slot/spins")
public class SlotController {
    private final SlotService service;

    /** 作用：注入SlotService。输入：业务服务。输出：Controller实例。逻辑：不直接访问Mapper。 */
    public SlotController(SlotService service) { this.service = service; }

    /** 作用：生成首次或重转结果。输入：可空请求体与认证主体。输出：200 Spin快照。逻辑：权限由SLOT_SPIN控制。 */
    @PostMapping
    @PreAuthorize("hasAuthority('SLOT_SPIN')")
    public ApiResponse<SlotResponses.SpinData> spin(@RequestBody(required = false) SlotRequests.SpinRequest request,
                                                     Authentication authentication) {
        String previous = request == null || request.previousSpinId() == null ? null : request.previousSpinId().toString();
        return ApiResponse.ok(service.spin(authentication.getName(), previous), "获取成功");
    }

    /** 作用：确认结果并返回Diet记录。输入：Spin UUID、确认表单和认证主体。输出：首次或幂等200响应。逻辑：权限由SLOT_CONFIRM控制。 */
    @PostMapping("/{spinId}/confirm")
    @PreAuthorize("hasAuthority('SLOT_CONFIRM')")
    public ApiResponse<DietResponses.DietRecordData> confirm(@PathVariable UUID spinId,
            @RequestBody SlotRequests.ConfirmRequest request, Authentication authentication) {
        return ApiResponse.ok(service.confirm(authentication.getName(), spinId.toString(), request), "确认成功");
    }
}

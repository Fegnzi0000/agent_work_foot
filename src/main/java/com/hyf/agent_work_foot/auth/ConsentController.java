package com.hyf.agent_work_foot.auth;
import com.hyf.agent_work_foot.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me/consent")
@PreAuthorize("hasRole('USER')")
public class ConsentController {
    private final ConsentService service;
    public ConsentController(ConsentService service) { this.service = service; }
    @GetMapping public ApiResponse<ConsentService.State> get(Authentication authentication) {
        return ApiResponse.ok(service.state(authentication.getName()), "获取成功");
    }
}

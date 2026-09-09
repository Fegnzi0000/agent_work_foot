package com.hyf.agent_work_foot.auth;

import com.hyf.agent_work_foot.common.ApiException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsumerAuthBoundaryTests {
    @Test void rejectsLegacyCodeOnlyLogin() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertFalse(factory.getValidator().validate(new AuthRequests.WeChatMiniProgramLoginRequest("code")).isEmpty());
        }
    }
    @Test void publicEmailEndpointsAreClosedBeforeServiceInvocation() {
        var service = mock(AuthService.class);
        var controller = new AuthController(service, mock(AuthRateLimiter.class));
        var http = new MockHttpServletRequest();
        assertThrows(ApiException.class, () -> controller.register(new AuthRequests.RegisterRequest("a@b.com", "Pass_123", "Pass_123"), http));
        assertThrows(ApiException.class, () -> controller.login(new AuthRequests.LoginRequest("a@b.com", "Pass_123"), http));
        assertThrows(ApiException.class, () -> controller.bindWeChatMiniProgram(new AuthRequests.BindWeChatMiniProgramRequest("code", "a@b.com", "Pass_123"), http));
        verifyNoInteractions(service);
    }
}

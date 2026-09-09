package com.hyf.agent_work_foot.admin;
import com.hyf.agent_work_foot.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ConsumerPasswordBoundaryTests {
    @Test void consumerTemporaryPasswordEndpointIsClosed() {
        var service = mock(AdminService.class);
        var authentication = mock(org.springframework.security.core.Authentication.class);
        when(authentication.getName()).thenReturn("admin");
        assertThrows(ApiException.class, () -> new AdminController(service).createTemporaryPassword(UUID.randomUUID(), authentication, new MockHttpServletRequest()));
        verifyNoInteractions(service);
    }
}

package com.hyf.agent_work_foot;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class AccountSecurityHttpContractTests extends AbstractMySqlIntegrationTest {
    @Test void consumersCannotChangePasswordsAndLegacyCancellationIsClosed() throws Exception {
        String token=register("security").path("data").path("accessToken").asText();
        assertEquals(403,perform(MockMvcRequestBuilders.post("/api/v1/users/me/change-password"),Map.of("currentPassword","Pass_123","newPassword","Other_123","confirmNewPassword","Other_123"),token).getResponse().getStatus());
        assertEquals(410,perform(MockMvcRequestBuilders.post("/api/v1/users/me/cancel"),Map.of("currentPassword","Pass_123","confirmation","CANCEL"),token).getResponse().getStatus());
    }
    @Test void administratorCanChangePasswordAndOldSessionBecomesInvalid() throws Exception {
        var admin=adminAccount();
        var change=Map.of("currentPassword",admin.password(),"newPassword","Other_123","confirmNewPassword","Other_123");
        assertEquals(200,perform(MockMvcRequestBuilders.post("/api/v1/users/me/change-password"),change,admin.accessToken()).getResponse().getStatus());
        assertEquals(401,perform(MockMvcRequestBuilders.get("/api/v1/users/me"),null,admin.accessToken()).getResponse().getStatus());
        assertEquals(200,perform(MockMvcRequestBuilders.post("/api/v1/admin/auth/login"),Map.of("account",admin.account(),"password","Other_123"),null).getResponse().getStatus());
    }
    @Test void administratorCannotUseConsumerCancellation() throws Exception {
        assertEquals(403,perform(MockMvcRequestBuilders.post("/api/v1/users/me/cancel-wechat"),Map.of("code","code","confirmation","CANCEL"),adminToken()).getResponse().getStatus());
    }
}

package com.hyf.agent_work_foot;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class AdminHttpContractTests extends AbstractMySqlIntegrationTest {
    @Test void separatesAdminAndConsumerPermissions() throws Exception {
        String user=register("permissions").path("data").path("accessToken").asText();
        assertEquals(403,perform(MockMvcRequestBuilders.get("/api/v1/admin/users"),null,user).getResponse().getStatus());
        assertEquals(403,perform(MockMvcRequestBuilders.get("/api/v1/food-options"),null,adminToken()).getResponse().getStatus());
    }
    @Test void disableInvalidatesSessionAndReenableDoesNotRestoreOldToken() throws Exception {
        var user=register("disable").path("data"); String token=user.path("accessToken").asText();
        String path="/api/v1/admin/users/"+user.path("user").path("id").asText()+"/status";
        String admin=adminToken();
        assertEquals(200,perform(MockMvcRequestBuilders.patch(path),Map.of("status","DISABLED"),admin).getResponse().getStatus());
        assertEquals(401,perform(MockMvcRequestBuilders.get("/api/v1/users/me"),null,token).getResponse().getStatus());
        assertEquals(200,perform(MockMvcRequestBuilders.patch(path),Map.of("status","ACTIVE"),admin).getResponse().getStatus());
        assertEquals(401,perform(MockMvcRequestBuilders.get("/api/v1/users/me"),null,token).getResponse().getStatus());
    }
    @Test void temporaryPasswordEndpointIsGoneAndAdministratorsCannotBeManagedAsConsumers() throws Exception {
        var user=register("no-temp").path("data"); String admin=adminToken();
        assertEquals(410,perform(MockMvcRequestBuilders.post("/api/v1/admin/users/"+user.path("user").path("id").asText()+"/temporary-password"),null,admin).getResponse().getStatus());
        var target=adminAccount();
        assertEquals(403,perform(MockMvcRequestBuilders.patch("/api/v1/admin/users/"+target.id()+"/status"),Map.of("status","DISABLED"),admin).getResponse().getStatus());
    }
}

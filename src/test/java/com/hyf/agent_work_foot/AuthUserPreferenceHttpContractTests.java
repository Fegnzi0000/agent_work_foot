package com.hyf.agent_work_foot;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Current contracts replace removed public email lifecycle; history remains in Git. */
class AuthUserPreferenceHttpContractTests extends AbstractMySqlIntegrationTest {
    @Test void closesPublicEmailRoutes() throws Exception {
        var request=Map.of("email","closed@example.com","password","Pass_123","confirmPassword","Pass_123");
        assertEquals(410,perform(MockMvcRequestBuilders.post("/api/v1/auth/register"),request,null).getResponse().getStatus());
        assertEquals(410,perform(MockMvcRequestBuilders.post("/api/v1/auth/login"),Map.of("email","closed@example.com","password","Pass_123"),null).getResponse().getStatus());
    }
    @Test void rejectsMissingAgreementBeforeCallingWeChat() throws Exception {
        assertEquals(400,perform(MockMvcRequestBuilders.post("/api/v1/auth/wechat/mini-program/login"),Map.of("code","unused"),null).getResponse().getStatus());
    }
    @Test void keepsProfileAndPreferencesScopedToAuthenticatedUser() throws Exception {
        var user=register("preferences").path("data"); String token=user.path("accessToken").asText();
        assertEquals(200,perform(MockMvcRequestBuilders.patch("/api/v1/users/me/profile"),Map.of("nickname","个人昵称"),token).getResponse().getStatus());
        assertEquals(200,perform(MockMvcRequestBuilders.patch("/api/v1/users/me/preferences"),Map.of("dislikes",List.of(Map.of("type","CUSTOM","value","香菜"))),token).getResponse().getStatus());
        var read=json(perform(MockMvcRequestBuilders.get("/api/v1/users/me/preferences"),null,token)).path("data");
        assertEquals(1,read.path("dislikes").size());
        String other=register("other").path("data").path("accessToken").asText();
        assertEquals(0,json(perform(MockMvcRequestBuilders.get("/api/v1/users/me/preferences"),null,other)).path("data").path("dislikes").size());
    }
    @Test void refreshRotatesAndLogoutRevokesToken() throws Exception {
        var user=register("refresh").path("data");
        var old=Map.of("refreshToken",user.path("refreshToken").asText());
        var refreshed=perform(MockMvcRequestBuilders.post("/api/v1/auth/refresh"),old,null);
        assertEquals(200,refreshed.getResponse().getStatus());
        assertEquals(401,perform(MockMvcRequestBuilders.post("/api/v1/auth/refresh"),old,null).getResponse().getStatus());
        var next=Map.of("refreshToken",json(refreshed).path("data").path("refreshToken").asText());
        assertEquals(200,perform(MockMvcRequestBuilders.post("/api/v1/auth/logout"),next,null).getResponse().getStatus());
        assertEquals(401,perform(MockMvcRequestBuilders.post("/api/v1/auth/refresh"),next,null).getResponse().getStatus());
    }
}

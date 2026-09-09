package com.hyf.agent_work_foot;

import com.fasterxml.jackson.databind.*;
import com.hyf.agent_work_foot.auth.*;
import com.hyf.agent_work_foot.common.ApiException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Runs only against an explicitly provisioned isolated database, never the developer database. */
@SpringBootTest(properties={
    "spring.datasource.url=${CONSENT_TEST_DB_URL}", "spring.datasource.username=${CONSENT_TEST_DB_USER}", "spring.datasource.password=${CONSENT_TEST_DB_PASSWORD}",
    "app.auth.jwt.active-secret=consent-test-only-secret-012345678901234567890123456789", "spring.flyway.enabled=true",
    "app.auth.rate-limit.login-max-attempts=1000", "app.account-security.rate-limit.max-attempts=1000"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="CONSENT_TEST_DB_URL", matches=".*agent_work_foot_consent_test\\?.*")
class ConsentLifecycleIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired com.hyf.agent_work_foot.admin.LocalAccountMaintenance maintenance;
    @MockitoBean WeChatMiniProgramClient wechat;
    @BeforeEach void identities() { when(wechat.exchangeCode(anyString())).thenAnswer(call -> new WeChatMiniProgramClient.WeChatIdentity(call.getArgument(0), null)); }
    private JsonNode login(String code, String age) throws Exception {
        var body = Map.of("code",code,"accepted",true,"termsVersion",ConsentService.VERSION,"privacyVersion",ConsentService.VERSION,"ageBand",age);
        return json.readTree(mvc.perform(post("/api/v1/auth/wechat/mini-program/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
    }
    @Test void minorCannotUpgradeAgeByLoggingInAgain() throws Exception {
        String code=UUID.randomUUID().toString(); var user=login(code,"AGE_14_17");
        assertThrows(ApiException.class, () -> auth.loginWithWeChatMiniProgram(new AuthRequests.WeChatMiniProgramLoginRequest(code,true,ConsentService.VERSION,ConsentService.VERSION,"ADULT")));
    }
    @Test void oldEmailSessionCannotAccessBusinessOrRefresh() throws Exception {
        var old=auth.register(new AuthRequests.RegisterRequest(UUID.randomUUID()+"@example.com","Pass_123","Pass_123"));
        mvc.perform(get("/api/v1/food-options").header("Authorization","Bearer "+old.accessToken())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("refreshToken",old.refreshToken())))).andExpect(status().isUnauthorized());
    }
    @Test void cancellationRequiresSameWeChatIdentityAndRevokesSession() throws Exception {
        String code=UUID.randomUUID().toString(); var user=login(code,"ADULT"); String bearer="Bearer "+user.get("accessToken").asText();
        mvc.perform(post("/api/v1/users/me/cancel-wechat").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"someone-else\",\"confirmation\":\"CANCEL\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users/me/cancel-wechat").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("code",code,"confirmation","CANCEL")))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/me").header("Authorization",bearer)).andExpect(status().isUnauthorized());
    }
    @Test void localAdminCreationIsIdempotentAndDoesNotResetPassword() {
        String name="adm_"+UUID.randomUUID().toString().replace("-", "").substring(0,10);
        String id=maintenance.createAdmin(name,"Pass_123");
        assertNotNull(id);
        assertEquals(id,maintenance.createAdmin(name,"Other_123"));
        assertEquals("ADMIN",auth.loginAdmin(new AuthRequests.AdminLoginRequest(name,"Pass_123")).user().role());
        assertThrows(ApiException.class,()->auth.loginAdmin(new AuthRequests.AdminLoginRequest(name,"Other_123")));
    }
    @Test void maintenanceNeverDeletesAnActiveUser() throws Exception {
        var user=login(UUID.randomUUID().toString(),"ADULT");
        String id=user.get("user").get("id").asText();
        assertThrows(IllegalStateException.class,()->maintenance.purgeCancelled(id,"PURGE "+id));
    }
    @Test void maintenanceClearsCancelledIdentityAndAllowsFreshRegistration() throws Exception {
        String code=UUID.randomUUID().toString(); var user=login(code,"ADULT");
        String id=user.get("user").get("id").asText();
        mvc.perform(post("/api/v1/users/me/cancel-wechat").header("Authorization","Bearer "+user.get("accessToken").asText()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("code",code,"confirmation","CANCEL")))).andExpect(status().isOk());
        maintenance.purgeCancelled(id,"PURGE "+id);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM user_identities WHERE user_id=?",Integer.class,id));
        assertNotEquals(id,login(code,"ADULT").get("user").get("id").asText());
    }
}

package com.hyf.agent_work_foot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.hyf.agent_work_foot.food.mapper.FoodDefaultTemplateMapper;
import com.hyf.agent_work_foot.preference.mapper.PreferenceMapper;
import com.hyf.agent_work_foot.user.mapper.UserMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 认证、当前用户和偏好模块的 HTTP 回归测试。
 *
 * <p>恢复 Food 开发前已有契约，并通过 Mapper 验证种子及账号状态，不使用 JdbcTemplate 或裸 SQL。</p>
 */
class AuthUserPreferenceHttpContractTests extends AbstractMySqlIntegrationTest {
    @Autowired
    private FoodDefaultTemplateMapper templateMapper;

    @Autowired
    private PreferenceMapper preferenceMapper;

    @Autowired
    private UserMapper userMapper;

    /**
     * 作用：验证 Flyway V1 种子数量。
     * 输入：Testcontainers 空数据库。输出：10 条食物模板和 16 条偏好预设。
     * 逻辑：使用各业务 Mapper 查询，避免测试绕过数据访问边界。
     */
    @Test
    void flywayInitializesReferenceDataThroughMappers() {
        assertEquals(10L, templateMapper.selectCount(null));
        assertEquals(16, preferenceMapper.selectActivePresets().size());
    }

    /**
     * 作用：验证注册、重复邮箱、登录失败、Refresh Token 轮换和退出。
     * 输入：独立邮箱和真实认证 HTTP 请求。输出：对应状态码、错误码及 requestId。
     * 逻辑：旧 Token 在轮换或退出后都不能再次使用。
     */
    @Test
    void supportsAuthenticationLifecycleAndRequestId() throws Exception {
        String email = "auth-flow@example.com";
        Map<String, String> registration = Map.of(
                "email", email,
                "password", "Pass_123",
                "confirmPassword", "Pass_123"
        );
        MvcResult registered = perform(MockMvcRequestBuilders.post("/api/v1/auth/register"), registration, null);
        assertEquals(201, registered.getResponse().getStatus());
        JsonNode created = json(registered);
        assertTrue(created.path("data").path("user").path("nickname").asText().startsWith("干饭用户"));
        assertEquals(created.path("requestId").asText(), registered.getResponse().getHeader("X-Request-Id"));
        String refreshToken = created.path("data").path("refreshToken").asText();

        MvcResult duplicate = perform(MockMvcRequestBuilders.post("/api/v1/auth/register"), registration, null);
        assertEquals(409, duplicate.getResponse().getStatus());
        assertEquals("EMAIL_ALREADY_REGISTERED", json(duplicate).path("code").asText());

        MvcResult badLogin = perform(MockMvcRequestBuilders.post("/api/v1/auth/login"), Map.of(
                "email", email,
                "password", "Wrong_12"
        ), null);
        assertEquals(401, badLogin.getResponse().getStatus());
        assertEquals("AUTH_INVALID_CREDENTIALS", json(badLogin).path("code").asText());

        MvcResult rotated = perform(MockMvcRequestBuilders.post("/api/v1/auth/refresh"),
                Map.of("refreshToken", refreshToken), null);
        assertEquals(200, rotated.getResponse().getStatus());
        String nextRefreshToken = json(rotated).path("data").path("refreshToken").asText();
        assertNotEquals(refreshToken, nextRefreshToken);
        assertEquals(401, perform(MockMvcRequestBuilders.post("/api/v1/auth/refresh"),
                Map.of("refreshToken", refreshToken), null).getResponse().getStatus());

        assertEquals(200, perform(MockMvcRequestBuilders.post("/api/v1/auth/logout"),
                Map.of("refreshToken", nextRefreshToken), null).getResponse().getStatus());
        assertEquals(401, perform(MockMvcRequestBuilders.post("/api/v1/auth/refresh"),
                Map.of("refreshToken", nextRefreshToken), null).getResponse().getStatus());
    }

    /**
     * 作用：验证当前用户、引导、偏好保存和预算约束。
     * 输入：真实 USER Token 与引导/补丁请求。输出：401、200 和 400。
     * 逻辑：用户归属只取认证主体，空列表表示清空对应偏好。
     */
    @Test
    void usesAuthenticatedUserForProfileAndPreferences() throws Exception {
        JsonNode registered = register("preference-flow");
        String accessToken = registered.path("data").path("accessToken").asText();
        assertEquals(401, perform(MockMvcRequestBuilders.get("/api/v1/users/me"), null, null)
                .getResponse().getStatus());
        assertEquals(200, perform(MockMvcRequestBuilders.get("/api/v1/preferences/options"), null, accessToken)
                .getResponse().getStatus());

        Map<String, Object> onboarding = Map.of(
                "nickname", "小饭",
                "budgetEnabled", true,
                "dailyBudget", "30",
                "medicalAllergies", List.of(Map.of("type", "PRESET", "value", "ALLERGY_EGG")),
                "dietaryRestrictions", List.of(Map.of(
                        "type", "PRESET", "value", "RESTRICTION_VEGETARIAN"
                )),
                "dislikes", List.of(),
                "tastePreferences", List.of(Map.of("type", "CUSTOM", "value", "微辣"))
        );
        assertEquals(200, perform(MockMvcRequestBuilders.put("/api/v1/users/me/onboarding"),
                onboarding, accessToken).getResponse().getStatus());
        JsonNode preferences = json(perform(MockMvcRequestBuilders.get("/api/v1/users/me/preferences"),
                null, accessToken));
        assertEquals("30.00", preferences.path("data").path("dailyBudget").asText());
        assertEquals("ALLERGY_EGG", preferences.path("data").path("medicalAllergies").get(0)
                .path("value").asText());

        assertEquals(400, perform(MockMvcRequestBuilders.patch("/api/v1/users/me/preferences"), Map.of(
                "budgetEnabled", false,
                "dailyBudget", "20"
        ), accessToken).getResponse().getStatus());
        assertEquals(400, perform(MockMvcRequestBuilders.patch("/api/v1/users/me/preferences"), Map.of(
                "dailyBudget", 20
        ), accessToken).getResponse().getStatus());
        assertEquals(200, perform(MockMvcRequestBuilders.patch("/api/v1/users/me/preferences"),
                Map.of("dislikes", List.of()), accessToken).getResponse().getStatus());
    }

    /**
     * 作用：验证禁用账号拒绝登录及登录限流。
     * 输入：注册用户 ID 和独立限流邮箱。输出：401 与 429。
     * 逻辑：账号状态通过真实 UserMapper 修改，限流以 IP+邮箱窗口累计。
     */
    @Test
    void rejectsDisabledAccountAndRateLimitedLogin() throws Exception {
        JsonNode registered = register("disabled-user");
        String userId = registered.path("data").path("user").path("id").asText();
        String email = registered.path("data").path("user").path("email").asText();
        String accessToken = registered.path("data").path("accessToken").asText();
        assertEquals(1, userMapper.updateStatus(userId, "DISABLED"));
        assertEquals(401, perform(MockMvcRequestBuilders.get("/api/v1/users/me"), null, accessToken)
                .getResponse().getStatus());
        assertEquals(401, perform(MockMvcRequestBuilders.post("/api/v1/auth/login"), Map.of(
                "email", email,
                "password", "Pass_123"
        ), null).getResponse().getStatus());

        MvcResult last = null;
        for (int index = 0; index < 11; index++) {
            last = perform(MockMvcRequestBuilders.post("/api/v1/auth/login"), Map.of(
                    "email", "limited-login@example.com",
                    "password", "Pass_123"
            ), null);
        }
        assertEquals(429, last.getResponse().getStatus());
        JsonNode error = json(last);
        assertEquals("RATE_LIMITED", error.path("code").asText());
        assertFalse(error.path("requestId").asText().isBlank());
    }
}

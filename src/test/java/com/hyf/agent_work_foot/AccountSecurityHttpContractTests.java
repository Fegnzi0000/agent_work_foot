package com.hyf.agent_work_foot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.hyf.agent_work_foot.auth.AccountSecurityService;
import com.hyf.agent_work_foot.auth.mapper.AccountSecurityMapper;
import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.food.mapper.FoodOptionMapper;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** 当前用户改密、注销、JWT即时失效、强制改密和账号安全限流的HTTP契约测试。 */
class AccountSecurityHttpContractTests extends AbstractMySqlIntegrationTest {
    @Autowired
    private AccountSecurityMapper accountSecurityMapper;

    @Autowired
    private AccountSecurityService accountSecurityService;

    @Autowired
    private FoodOptionMapper foodOptionMapper;

    /**
     * 作用：验证改密规则和全部旧会话立即失效。
     * 输入：真实注册用户、错误表单和最终合法表单。输出：稳定错误码、200以及新密码登录成功。
     * 逻辑：覆盖密码错误、确认不一致、新旧相同、Access版本失效和Refresh批量撤销。
     */
    @Test
    void changesPasswordAndInvalidatesEveryOldToken() throws Exception {
        JsonNode registered = register("change-password").path("data");
        String accessToken = registered.path("accessToken").asText();
        String refreshToken = registered.path("refreshToken").asText();
        String email = registered.path("user").path("email").asText();

        MvcResult wrong = changePassword(accessToken, "Wrong_12", "NewPass_1", "NewPass_1");
        assertEquals(400, wrong.getResponse().getStatus());
        assertEquals("CURRENT_PASSWORD_INCORRECT", json(wrong).path("code").asText());

        MvcResult mismatch = changePassword(accessToken, "Pass_123", "NewPass_1", "NewPass_2");
        assertEquals(400, mismatch.getResponse().getStatus());
        assertEquals("PASSWORD_CONFIRMATION_MISMATCH", json(mismatch).path("code").asText());

        MvcResult unchanged = changePassword(accessToken, "Pass_123", "Pass_123", "Pass_123");
        assertEquals(400, unchanged.getResponse().getStatus());
        assertEquals("PASSWORD_UNCHANGED", json(unchanged).path("code").asText());

        assertEquals(200, changePassword(accessToken, "Pass_123", "NewPass_1", "NewPass_1")
                .getResponse().getStatus());
        assertEquals(401, perform(MockMvcRequestBuilders.get("/api/v1/users/me"), null, accessToken)
                .getResponse().getStatus());
        MvcResult oldRefresh = perform(MockMvcRequestBuilders.post("/api/v1/auth/refresh"),
                Map.of("refreshToken", refreshToken), null);
        assertEquals(401, oldRefresh.getResponse().getStatus());
        assertEquals("AUTH_TOKEN_INVALID", json(oldRefresh).path("code").asText());
        assertEquals(401, login(email, "Pass_123").getResponse().getStatus());
        assertEquals(200, login(email, "NewPass_1").getResponse().getStatus());
    }

    /**
     * 作用：验证软注销状态、数据保留和认证不可恢复。
     * 输入：有默认食物的注册用户。输出：CANCELLED、Token失效、邮箱不可重注册且食物仍存在。
     * 逻辑：注销只更新账号安全数据，不删除业务表。
     */
    @Test
    void cancelsAccountAndRetainsBusinessData() throws Exception {
        JsonNode registered = register("cancel-account").path("data");
        String accessToken = registered.path("accessToken").asText();
        String refreshToken = registered.path("refreshToken").asText();
        String userId = registered.path("user").path("id").asText();
        String email = registered.path("user").path("email").asText();
        int foodCount = foodOptionMapper.selectAllOwnedActive(userId).size();

        MvcResult wrongConfirmation = perform(MockMvcRequestBuilders.post("/api/v1/users/me/cancel"),
                Map.of("currentPassword", "Pass_123", "confirmation", "cancel"), accessToken);
        assertEquals(400, wrongConfirmation.getResponse().getStatus());

        MvcResult wrongPassword = perform(MockMvcRequestBuilders.post("/api/v1/users/me/cancel"),
                Map.of("currentPassword", "Wrong_12", "confirmation", "CANCEL"), accessToken);
        assertEquals(400, wrongPassword.getResponse().getStatus());
        assertEquals("CURRENT_PASSWORD_INCORRECT", json(wrongPassword).path("code").asText());

        MvcResult cancelled = perform(MockMvcRequestBuilders.post("/api/v1/users/me/cancel"),
                Map.of("currentPassword", "Pass_123", "confirmation", "CANCEL"), accessToken);
        assertEquals(200, cancelled.getResponse().getStatus());
        assertEquals("CANCELLED", accountSecurityMapper.selectAccessState(userId).status());
        assertEquals(1, accountSecurityMapper.selectAccessState(userId).authVersion());
        assertEquals(foodCount, foodOptionMapper.selectAllOwnedActive(userId).size());
        assertEquals(401, perform(MockMvcRequestBuilders.get("/api/v1/users/me"), null, accessToken)
                .getResponse().getStatus());
        assertEquals(401, perform(MockMvcRequestBuilders.post("/api/v1/auth/refresh"),
                Map.of("refreshToken", refreshToken), null).getResponse().getStatus());
        assertEquals(401, login(email, "Pass_123").getResponse().getStatus());
        assertEquals(409, perform(MockMvcRequestBuilders.post("/api/v1/auth/register"), Map.of(
                "email", email, "password", "Pass_123", "confirmPassword", "Pass_123"), null)
                .getResponse().getStatus());
        assertEquals(401, perform(MockMvcRequestBuilders.post("/api/v1/users/me/cancel"),
                Map.of("currentPassword", "Pass_123", "confirmation", "CANCEL"), accessToken)
                .getResponse().getStatus());
    }

    /**
     * 作用：验证强制改密白名单以及改密后的限制解除。
     * 输入：管理员为真实USER生成的一次性临时密码。输出：资料200、业务403、注销403和改密200。
     * 逻辑：通过完整Admin重置和临时登录流程进入mustChangePassword状态，不绕过生产凭据语义。
     */
    @Test
    void restrictsAccountUntilRequiredPasswordChange() throws Exception {
        JsonNode registered = register("required-change").path("data");
        String userId = registered.path("user").path("id").asText();
        String email = registered.path("user").path("email").asText();
        MvcResult created = perform(MockMvcRequestBuilders.post(
                "/api/v1/admin/users/" + userId + "/temporary-password"), null, adminToken());
        assertEquals(201, created.getResponse().getStatus());
        String temporaryPassword = json(created).path("data").path("temporaryPassword").asText();
        JsonNode forcedLogin = json(login(email, temporaryPassword)).path("data");
        assertEquals("CHANGE_PASSWORD", forcedLogin.path("nextStep").asText());
        String restrictedToken = forcedLogin.path("accessToken").asText();
        String refreshToken = forcedLogin.path("refreshToken").asText();

        MvcResult refreshed = perform(MockMvcRequestBuilders.post("/api/v1/auth/refresh"),
                Map.of("refreshToken", refreshToken), null);
        assertEquals(200, refreshed.getResponse().getStatus());
        String rotatedRefresh = json(refreshed).path("data").path("refreshToken").asText();
        assertEquals(200, perform(MockMvcRequestBuilders.post("/api/v1/auth/logout"),
                Map.of("refreshToken", rotatedRefresh), null).getResponse().getStatus());

        assertEquals(200, perform(MockMvcRequestBuilders.get("/api/v1/users/me"), null, restrictedToken)
                .getResponse().getStatus());
        MvcResult food = perform(MockMvcRequestBuilders.get("/api/v1/food-options"), null, restrictedToken);
        assertEquals(403, food.getResponse().getStatus());
        assertEquals("PASSWORD_CHANGE_REQUIRED", json(food).path("code").asText());
        MvcResult cancel = perform(MockMvcRequestBuilders.post("/api/v1/users/me/cancel"),
                Map.of("currentPassword", temporaryPassword, "confirmation", "CANCEL"), restrictedToken);
        assertEquals(403, cancel.getResponse().getStatus());
        assertEquals("PASSWORD_CHANGE_REQUIRED", json(cancel).path("code").asText());
        assertEquals(200, changePassword(restrictedToken, temporaryPassword, "NewPass_2", "NewPass_2")
                .getResponse().getStatus());
        assertEquals(401, perform(MockMvcRequestBuilders.get("/api/v1/users/me"), null, restrictedToken)
                .getResponse().getStatus());
    }

    /** 作用：验证账号安全请求的严格JSON契约。输入：空对象、显式null和未知字段。输出：统一400及requestId。逻辑：业务方法执行前拒绝不完整或扩展字段。 */
    @Test
    void rejectsMalformedAccountSecurityRequests() throws Exception {
        String token = register("security-json").path("data").path("accessToken").asText();
        MvcResult empty = perform(MockMvcRequestBuilders.post("/api/v1/users/me/change-password"),
                Map.of(), token);
        assertEquals(400, empty.getResponse().getStatus());
        assertEquals("VALIDATION_FAILED", json(empty).path("code").asText());

        var explicitNull = objectMapper.createObjectNode();
        explicitNull.putNull("currentPassword");
        explicitNull.put("newPassword", "NewPass_1");
        explicitNull.put("confirmNewPassword", "NewPass_1");
        assertEquals(400, perform(MockMvcRequestBuilders.post("/api/v1/users/me/change-password"),
                explicitNull, token).getResponse().getStatus());

        MvcResult unknown = perform(MockMvcRequestBuilders.post("/api/v1/users/me/change-password"), Map.of(
                "currentPassword", "Pass_123",
                "newPassword", "NewPass_1",
                "confirmNewPassword", "NewPass_1",
                "unexpected", true
        ), token);
        assertEquals(400, unknown.getResponse().getStatus());
        assertEquals(unknown.getResponse().getHeader("X-Request-Id"), json(unknown).path("requestId").asText());
    }

    /** 作用：验证管理员只能改自己的密码而不能普通注销。输入：真实ADMIN夹具。输出：注销403、改密200。逻辑：权限来自数据库当前角色。 */
    @Test
    void letsAdminChangePasswordButNotCancelAccount() throws Exception {
        AdminAccount admin = adminAccount();
        assertEquals(403, perform(MockMvcRequestBuilders.post("/api/v1/users/me/cancel"),
                Map.of("currentPassword", admin.password(), "confirmation", "CANCEL"), admin.accessToken())
                .getResponse().getStatus());
        assertEquals(200, changePassword(admin.accessToken(), admin.password(), "Admin_456", "Admin_456")
                .getResponse().getStatus());
        assertEquals(200, login(admin.email(), "Admin_456").getResponse().getStatus());
    }

    /** 作用：验证账号安全限流。输入：同一用户和IP连续六次错误密码。输出：前五次400、第六次429。逻辑：改密与注销共享固定窗口。 */
    @Test
    void rateLimitsPasswordVerificationAttempts() throws Exception {
        String token = register("security-limit").path("data").path("accessToken").asText();
        for (int index = 0; index < 5; index++) {
            assertEquals(400, changePassword(token, "Wrong_12", "Limit_12", "Limit_12")
                    .getResponse().getStatus());
        }
        MvcResult limited = changePassword(token, "Wrong_12", "Limit_12", "Limit_12");
        assertEquals(429, limited.getResponse().getStatus());
        assertEquals("RATE_LIMITED", json(limited).path("code").asText());
    }

    /** 作用：验证并发改密的用户行锁。输入：同一旧密码的两个并发服务调用。输出：一个成功、一个当前密码错误。逻辑：第二个事务在锁后读取新哈希。 */
    @Test
    void serializesConcurrentPasswordChanges() throws Exception {
        JsonNode registered = register("concurrent-change").path("data");
        String userId = registered.path("user").path("id").asText();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> concurrentChange(userId, ready, start));
            Future<String> second = executor.submit(() -> concurrentChange(userId, ready, start));
            ready.await();
            start.countDown();
            Set<String> results = new HashSet<>();
            results.add(first.get());
            results.add(second.get());
            assertEquals(Set.of("OK", "CURRENT_PASSWORD_INCORRECT"), results);
        } finally {
            executor.shutdownNow();
        }
        assertTrue(accountSecurityMapper.selectAccessState(userId).authVersion() > 0);
    }

    /** 作用：执行改密HTTP请求。输入：Token及三个密码字段。输出：MVC结果。逻辑：减少场景测试的重复请求构造。 */
    private MvcResult changePassword(String token, String current, String next, String confirmation) throws Exception {
        return perform(MockMvcRequestBuilders.post("/api/v1/users/me/change-password"), Map.of(
                "currentPassword", current,
                "newPassword", next,
                "confirmNewPassword", confirmation
        ), token);
    }

    /** 作用：执行登录请求。输入：邮箱和密码。输出：MVC结果。逻辑：验证改密或注销后的认证表现。 */
    private MvcResult login(String email, String password) throws Exception {
        return perform(MockMvcRequestBuilders.post("/api/v1/auth/login"),
                Map.of("email", email, "password", password), null);
    }

    /** 作用：同步起跑一次改密服务调用。输入：用户和栅栏。输出：OK或业务错误码。逻辑：直接验证事务行锁而不受HTTP前置版本校验时序影响。 */
    private String concurrentChange(String userId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            accountSecurityService.changePassword(userId, "Pass_123", "Race_123", "Race_123");
            return "OK";
        } catch (ApiException exception) {
            return exception.code();
        }
    }
}

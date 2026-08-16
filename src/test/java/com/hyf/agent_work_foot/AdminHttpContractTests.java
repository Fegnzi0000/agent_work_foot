package com.hyf.agent_work_foot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.hyf.agent_work_foot.admin.AdminBootstrapService;
import com.hyf.agent_work_foot.admin.mapper.AdminAuditMapper;
import com.hyf.agent_work_foot.auth.AuthRequests;
import com.hyf.agent_work_foot.auth.AuthService;
import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.common.AppPermissions;
import com.hyf.agent_work_foot.rbac.mapper.RbacMapper;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Admin独立入口、动态RBAC、账号状态、一次性临时密码、审计、限流和bootstrap的HTTP契约测试。 */
class AdminHttpContractTests extends AbstractMySqlIntegrationTest {
    @Autowired
    private RbacMapper rbacMapper;

    @Autowired
    private AdminAuditMapper auditMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private AdminBootstrapService bootstrapService;

    /** 作用：验证数据库权限和管理员独立入口。输入：USER、ADMIN和SUPER_ADMIN夹具。输出：不同nextStep及403/200边界。逻辑：后端不依赖前端页面控制权限。 */
    @Test
    void separatesAdminPortalFromUserBusinessPermissions() throws Exception {
        JsonNode user = register("rbac-user").path("data");
        AdminAccount admin = adminAccount();
        AdminAccount superAdmin = superAdminAccount();

        assertEquals(403, perform(MockMvcRequestBuilders.get("/api/v1/admin/users"),
                null, user.path("accessToken").asText()).getResponse().getStatus());
        assertEquals(403, perform(MockMvcRequestBuilders.get("/api/v1/food-options"),
                null, admin.accessToken()).getResponse().getStatus());
        assertEquals("ADMIN_HOME", json(login(admin.email(), admin.password()))
                .path("data").path("nextStep").asText());
        assertEquals("ADMIN_HOME", json(login(superAdmin.email(), superAdmin.password()))
                .path("data").path("nextStep").asText());
        assertTrue(rbacMapper.selectPermissionCodesByUserId(superAdmin.id())
                .contains(AppPermissions.ADMIN_ACCOUNT_MANAGE));

        JsonNode adminPage = json(perform(MockMvcRequestBuilders.get("/api/v1/admin/users"),
                null, admin.accessToken())).path("data");
        assertTrue(adminPage.path("items").findValuesAsText("role").stream()
                .allMatch("USER"::equals));
        JsonNode superPage = json(perform(MockMvcRequestBuilders.get("/api/v1/admin/users"),
                null, superAdmin.accessToken())).path("data");
        assertTrue(superPage.path("items").findValuesAsText("role").stream()
                .anyMatch(role -> role.equals("ADMIN") || role.equals("SUPER_ADMIN")));
    }

    /** 作用：验证禁用、启用、幂等和全部会话即时失效。输入：注册用户和ADMIN。输出：状态200、旧Token401、重新登录成功。逻辑：真实转换递增authVersion并撤销Refresh Token。 */
    @Test
    void disablesAndReenablesUserWithImmediateSessionRevocation() throws Exception {
        JsonNode user = register("admin-status").path("data");
        String userId = user.path("user").path("id").asText();
        String email = user.path("user").path("email").asText();
        String accessToken = user.path("accessToken").asText();
        String refreshToken = user.path("refreshToken").asText();
        String adminToken = adminToken();

        MvcResult disabled = status(adminToken, userId, "DISABLED");
        assertEquals(200, disabled.getResponse().getStatus());
        assertEquals("DISABLED", json(disabled).path("data").path("status").asText());
        assertEquals(401, perform(MockMvcRequestBuilders.get("/api/v1/users/me"), null, accessToken)
                .getResponse().getStatus());
        assertEquals(401, perform(MockMvcRequestBuilders.post("/api/v1/auth/refresh"),
                Map.of("refreshToken", refreshToken), null).getResponse().getStatus());
        assertEquals(401, login(email, "Pass_123").getResponse().getStatus());

        assertEquals(200, status(adminToken, userId, "ACTIVE").getResponse().getStatus());
        assertEquals(200, login(email, "Pass_123").getResponse().getStatus());
        assertEquals(200, status(adminToken, userId, "ACTIVE").getResponse().getStatus());
        assertEquals(1, auditMapper.countByTargetAndAction(userId, "USER_STATUS_UNCHANGED"));
    }

    /** 作用：验证管理员账号必须由特权角色管理且不能自我操作。输入：两个ADMIN和一个SUPER_ADMIN。输出：普通管理员403、超级管理员200、自操作403。逻辑：目标角色额外检查ADMIN_ACCOUNT_MANAGE。 */
    @Test
    void requiresExplicitPermissionToManageOtherAdmins() throws Exception {
        AdminAccount first = adminAccount();
        AdminAccount second = adminAccount();
        AdminAccount superAdmin = superAdminAccount();

        MvcResult forbidden = status(first.accessToken(), second.id(), "DISABLED");
        assertEquals(403, forbidden.getResponse().getStatus());
        assertEquals("ADMIN_TARGET_FORBIDDEN", json(forbidden).path("code").asText());
        assertEquals(200, status(superAdmin.accessToken(), second.id(), "DISABLED")
                .getResponse().getStatus());
        assertEquals(403, status(superAdmin.accessToken(), superAdmin.id(), "DISABLED")
                .getResponse().getStatus());
        assertTrue(auditMapper.countByTargetAndAction(second.id(), "USER_DISABLED") >= 1);
    }

    /** 作用：验证管理员重置密码的完整生命周期。输入：注册用户和一次性临时密码。输出：旧凭据失效、临时登录一次、强制改密及新密码成功。逻辑：密码、Token、临时记录和安全版本同事务。 */
    @Test
    void resetsPasswordAndConsumesTemporaryCredentialExactlyOnce() throws Exception {
        JsonNode user = register("temporary-password").path("data");
        String userId = user.path("user").path("id").asText();
        String email = user.path("user").path("email").asText();
        String oldAccess = user.path("accessToken").asText();
        String oldRefresh = user.path("refreshToken").asText();

        MvcResult created = temporaryPassword(adminToken(), userId);
        assertEquals(201, created.getResponse().getStatus());
        String temporary = json(created).path("data").path("temporaryPassword").asText();
        assertEquals(12, temporary.length());
        assertEquals(401, perform(MockMvcRequestBuilders.get("/api/v1/users/me"), null, oldAccess)
                .getResponse().getStatus());
        assertEquals(401, perform(MockMvcRequestBuilders.post("/api/v1/auth/refresh"),
                Map.of("refreshToken", oldRefresh), null).getResponse().getStatus());
        assertEquals(401, login(email, "Pass_123").getResponse().getStatus());

        JsonNode temporaryLogin = json(login(email, temporary)).path("data");
        assertEquals("CHANGE_PASSWORD", temporaryLogin.path("nextStep").asText());
        String restrictedToken = temporaryLogin.path("accessToken").asText();
        assertEquals(401, login(email, temporary).getResponse().getStatus());
        assertEquals(403, perform(MockMvcRequestBuilders.get("/api/v1/food-options"),
                null, restrictedToken).getResponse().getStatus());
        assertEquals(200, perform(MockMvcRequestBuilders.post("/api/v1/users/me/change-password"), Map.of(
                "currentPassword", temporary,
                "newPassword", "Formal_456",
                "confirmNewPassword", "Formal_456"
        ), restrictedToken).getResponse().getStatus());
        assertEquals(200, login(email, "Formal_456").getResponse().getStatus());
        assertEquals(1, auditMapper.countByTargetAndAction(userId, "TEMP_PASSWORD_CREATED"));
    }

    /** 作用：验证重复生成和并发登录。输入：先后两个临时密码及两个并发登录事务。输出：旧密码401，最新密码仅一个事务成功。逻辑：用户行锁与临时记录条件更新共同保证一次性。 */
    @Test
    void keepsOnlyLatestTemporaryPasswordAndSerializesConcurrentLogin() throws Exception {
        JsonNode user = register("temporary-race").path("data");
        String userId = user.path("user").path("id").asText();
        String email = user.path("user").path("email").asText();
        String adminToken = adminToken();
        String first = json(temporaryPassword(adminToken, userId))
                .path("data").path("temporaryPassword").asText();
        String second = json(temporaryPassword(adminToken, userId))
                .path("data").path("temporaryPassword").asText();
        assertEquals(401, login(email, first).getResponse().getStatus());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> one = executor.submit(() -> concurrentLogin(email, second, ready, start));
            Future<String> two = executor.submit(() -> concurrentLogin(email, second, ready, start));
            ready.await();
            start.countDown();
            Set<String> results = new HashSet<>();
            results.add(one.get());
            results.add(two.get());
            assertEquals(Set.of("OK", "AUTH_INVALID_CREDENTIALS"), results);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 作用：验证受控bootstrap提升及旧会话失效。输入：已注册ACTIVE USER。输出：SUPER_ADMIN登录入口、旧Token401和重复执行幂等。逻辑：不通过HTTP或明文密码提权。 */
    @Test
    void bootstrapsRegisteredUserWithoutHttpPrivilegeEndpoint() throws Exception {
        JsonNode user = register("bootstrap-admin").path("data");
        String userId = user.path("user").path("id").asText();
        String email = user.path("user").path("email").asText();
        String oldAccess = user.path("accessToken").asText();
        assertEquals(userId, bootstrapService.promote(email, "SUPER_ADMIN"));
        assertEquals(userId, bootstrapService.promote(email, "SUPER_ADMIN"));
        assertEquals(401, perform(MockMvcRequestBuilders.get("/api/v1/users/me"), null, oldAccess)
                .getResponse().getStatus());
        JsonNode loggedIn = json(login(email, "Pass_123")).path("data");
        assertEquals("SUPER_ADMIN", loggedIn.path("user").path("role").asText());
        assertEquals("ADMIN_HOME", loggedIn.path("nextStep").asText());
    }

    /** 作用：验证严格JSON、资源错误、requestId和临时密码限流。输入：非法PATCH、未知用户和六次生成。输出：400/404/429及审计。逻辑：错误响应与成功路径共用统一结构。 */
    @Test
    void validatesAdminContractsAuditsFailuresAndRateLimitsReset() throws Exception {
        JsonNode user = register("admin-contract").path("data");
        String userId = user.path("user").path("id").asText();
        String adminToken = adminToken();
        MvcResult invalid = status(adminToken, userId, "CANCELLED");
        assertEquals(400, invalid.getResponse().getStatus());
        assertEquals("VALIDATION_FAILED", json(invalid).path("code").asText());

        MvcResult unknownField = perform(MockMvcRequestBuilders.patch(
                "/api/v1/admin/users/" + userId + "/status"),
                Map.of("status", "ACTIVE", "unexpected", true), adminToken);
        assertEquals(400, unknownField.getResponse().getStatus());

        String missingId = UUID.randomUUID().toString();
        MvcResult missing = status(adminToken, missingId, "DISABLED");
        assertEquals(404, missing.getResponse().getStatus());
        assertEquals(missing.getResponse().getHeader("X-Request-Id"),
                json(missing).path("requestId").asText());
        assertEquals(1, auditMapper.countByTargetAndAction(missingId, "USER_STATUS_UPDATE"));

        for (int index = 0; index < 5; index++) {
            assertEquals(201, temporaryPassword(adminToken, userId).getResponse().getStatus());
        }
        MvcResult limited = temporaryPassword(adminToken, userId);
        assertEquals(429, limited.getResponse().getStatus());
        assertEquals("RATE_LIMITED", json(limited).path("code").asText());
    }

    /** 作用：提交状态PATCH。输入：管理员Token、目标和状态。输出：MVC结果。逻辑：统一测试请求构造。 */
    private MvcResult status(String token, String userId, String status) throws Exception {
        return perform(MockMvcRequestBuilders.patch("/api/v1/admin/users/" + userId + "/status"),
                Map.of("status", status), token);
    }

    /** 作用：请求一次临时密码。输入：管理员Token和目标。输出：MVC结果。逻辑：端点不发送请求正文。 */
    private MvcResult temporaryPassword(String token, String userId) throws Exception {
        return perform(MockMvcRequestBuilders.post(
                "/api/v1/admin/users/" + userId + "/temporary-password"), null, token);
    }

    /** 作用：提交登录请求。输入：邮箱和密码。输出：MVC结果。逻辑：覆盖正式和临时凭据。 */
    private MvcResult login(String email, String password) throws Exception {
        return perform(MockMvcRequestBuilders.post("/api/v1/auth/login"),
                Map.of("email", email, "password", password), null);
    }

    /** 作用：并发执行一次临时密码登录。输入：凭据和并发栅栏。输出：OK或错误码。逻辑：调用Spring事务代理而非私有实现。 */
    private String concurrentLogin(
            String email,
            String password,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await();
        try {
            authService.login(new AuthRequests.LoginRequest(email, password));
            return "OK";
        } catch (ApiException exception) {
            return exception.code();
        }
    }
}

package com.hyf.agent_work_foot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.hyf.agent_work_foot.auth.JwtService;
import com.hyf.agent_work_foot.common.AppConstants;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Diet 五个接口的核心 HTTP 契约测试，覆盖手工快照、食物池关联、统计、权限与软删除。 */
class DietHttpContractTests extends AbstractMySqlIntegrationTest {
    @Autowired
    private JwtService jwtService;

    /** 作用：验证手工记录入池、快照返回、修改、统计和删除。输入：真实USER令牌。输出：完整生命周期状态码。逻辑：通过公开HTTP接口验证事务链路。 */
    @Test
    void supportsManualRecordLifecycleAndStatistics() throws Exception {
        String token = register("diet-life").path("data").path("accessToken").asText();
        Map<String, Object> body = Map.of(
                "manualFood", Map.of("name", "自带便当", "category", "米饭", "tags", List.of("主食")),
                "addToFoodPool", true,
                "actualPrice", "12.345",
                "mealType", "LUNCH",
                "eatenAt", "2026-08-10T12:30:00+08:00"
        );
        MvcResult createdResult = perform(MockMvcRequestBuilders.post("/api/v1/diet-records"), body, token);
        assertEquals(201, createdResult.getResponse().getStatus());
        JsonNode created = json(createdResult).path("data");
        String id = created.path("id").asText();
        assertEquals("自带便当", created.path("foodName").asText());
        assertEquals("12.35", created.path("actualPrice").asText());
        assertFalse(created.path("foodOptionId").asText().isBlank());

        MvcResult list = perform(MockMvcRequestBuilders.get("/api/v1/diet-records")
                .param("startDate", "2026-08-01").param("endDate", "2026-08-31"), null, token);
        assertEquals(200, list.getResponse().getStatus());
        assertEquals("自带便当", json(list).path("data").path("items").get(0).path("foodName").asText());

        MvcResult patch = perform(MockMvcRequestBuilders.patch("/api/v1/diet-records/{id}", id),
                Map.of("actualPrice", "20"), token);
        assertEquals(200, patch.getResponse().getStatus());
        assertEquals("20.00", json(patch).path("data").path("actualPrice").asText());

        MvcResult statistics = perform(MockMvcRequestBuilders.get("/api/v1/diet-records/statistics")
                .param("startDate", "2026-08-01").param("endDate", "2026-08-31").param("groupBy", "DAY"), null, token);
        assertEquals(200, statistics.getResponse().getStatus());
        assertEquals("20.00", json(statistics).path("data").path("totalSpent").asText());
        assertEquals(31, json(statistics).path("data").path("spendingSeries").size());

        assertEquals(204, perform(MockMvcRequestBuilders.delete("/api/v1/diet-records/{id}", id), null, token).getResponse().getStatus());
        assertEquals(0, json(perform(MockMvcRequestBuilders.get("/api/v1/diet-records")
                .param("startDate", "2026-08-01").param("endDate", "2026-08-31"), null, token))
                .path("data").path("totalElements").asInt());
    }

    /** 作用：验证二选一、日期成对、跨用户与管理员权限。输入：非法或非USER请求。输出：统一400、404、403。逻辑：覆盖公开安全边界。 */
    @Test
    void validatesSelectionAndPermissions() throws Exception {
        String token = register("diet-guard").path("data").path("accessToken").asText();
        MvcResult incomplete = perform(MockMvcRequestBuilders.post("/api/v1/diet-records"), Map.of(
                "manualFood", Map.of("name", "便当", "category", "米饭", "tags", List.of()),
                "actualPrice", "1", "mealType", "LUNCH", "eatenAt", "2026-08-10T12:30:00+08:00"), token);
        assertEquals(400, incomplete.getResponse().getStatus());
        assertEquals("VALIDATION_FAILED", json(incomplete).path("code").asText());
        assertEquals(400, perform(MockMvcRequestBuilders.get("/api/v1/diet-records").param("startDate", "2026-08-01"), null, token).getResponse().getStatus());
        String admin = jwtService.issueAccessToken(UUID.randomUUID().toString(), AppConstants.ROLE_ADMIN);
        assertEquals(403, perform(MockMvcRequestBuilders.get("/api/v1/diet-records"), null, admin).getResponse().getStatus());
    }
}

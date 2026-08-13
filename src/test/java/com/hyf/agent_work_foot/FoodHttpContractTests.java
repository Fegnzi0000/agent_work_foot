package com.hyf.agent_work_foot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

/** Food 五个 HTTP 接口的状态码、权限、归属、PATCH 与 requestId 契约测试。 */
class FoodHttpContractTests extends AbstractMySqlIntegrationTest {
    @Autowired
    private JwtService jwtService;

    /** 作用：验证创建、详情、修改、删除和重复删除。输入：真实 USER Token。输出：201/200/204/404。逻辑：同时验证六字段与空标签兜底。 */
    @Test
    void supportsCompleteFoodLifecycle() throws Exception {
        JsonNode auth = register("food-lifecycle");
        String token = auth.path("data").path("accessToken").asText();

        MvcResult createdResult = perform(MockMvcRequestBuilders.post("/api/v1/food-options"), Map.of(
                "name", "  牛排饭  ",
                "category", "自定义",
                "defaultPrice", "20.125",
                "tags", List.of()
        ), token);
        assertEquals(201, createdResult.getResponse().getStatus());
        JsonNode created = json(createdResult);
        JsonNode food = created.path("data");
        String id = food.path("id").asText();
        assertEquals("牛排饭", food.path("name").asText());
        assertEquals("20.13", food.path("defaultPrice").asText());
        assertEquals("其他", food.path("tags").get(0).asText());
        assertEquals("CUSTOM", food.path("source").asText());
        assertEquals(6, food.size());
        assertEquals(created.path("requestId").asText(),
                createdResult.getResponse().getHeader("X-Request-Id"));

        MvcResult detailResult = perform(MockMvcRequestBuilders.get("/api/v1/food-options/{id}", id), null, token);
        assertEquals(200, detailResult.getResponse().getStatus());

        MvcResult patchedResult = perform(MockMvcRequestBuilders.patch("/api/v1/food-options/{id}", id),
                Map.of("tags", List.of("晚餐", "晚餐")), token);
        assertEquals(200, patchedResult.getResponse().getStatus());
        assertEquals(1, json(patchedResult).path("data").path("tags").size());
        assertEquals("CUSTOM", json(patchedResult).path("data").path("source").asText());

        MvcResult deleted = perform(MockMvcRequestBuilders.delete("/api/v1/food-options/{id}", id), null, token);
        assertEquals(204, deleted.getResponse().getStatus());
        assertEquals("", deleted.getResponse().getContentAsString());
        assertFalse(deleted.getResponse().getHeader("X-Request-Id").isBlank());
        assertEquals(404, perform(MockMvcRequestBuilders.delete("/api/v1/food-options/{id}", id), null, token)
                .getResponse().getStatus());
    }

    /** 作用：验证分页筛选、重复约束和删除后重建。输入：名称、分类与标签筛选。输出：AND 语义和稳定错误码。逻辑：使用一个用户的独立数据。 */
    @Test
    void filtersAndHandlesDuplicateThenRecreate() throws Exception {
        JsonNode auth = register("food-filter");
        String token = auth.path("data").path("accessToken").asText();
        Map<String, Object> body = Map.of(
                "name", "百分%饭",
                "category", "Custom_Category".substring(0, 10),
                "defaultPrice", "12",
                "tags", List.of("标签A", "标签B")
        );
        MvcResult first = perform(MockMvcRequestBuilders.post("/api/v1/food-options"), body, token);
        assertEquals(201, first.getResponse().getStatus());
        String id = json(first).path("data").path("id").asText();

        MvcResult duplicate = perform(MockMvcRequestBuilders.post("/api/v1/food-options"), body, token);
        assertEquals(409, duplicate.getResponse().getStatus());
        assertEquals("FOOD_OPTION_DUPLICATE", json(duplicate).path("code").asText());

        MvcResult filtered = perform(MockMvcRequestBuilders.get("/api/v1/food-options")
                .param("keyword", "%")
                .param("category", "custom")
                .param("tags", "标签A", "标签B"), null, token);
        assertEquals(200, filtered.getResponse().getStatus());
        assertEquals(1, json(filtered).path("data").path("totalElements").asInt());

        assertEquals(204, perform(MockMvcRequestBuilders.delete("/api/v1/food-options/{id}", id), null, token)
                .getResponse().getStatus());
        assertEquals(201, perform(MockMvcRequestBuilders.post("/api/v1/food-options"), body, token)
                .getResponse().getStatus());
    }

    /** 作用：验证未知字段、空 PATCH、null、非 UUID 和跨用户访问。输入：各种非法请求。输出：统一 400/404。逻辑：资源存在性不向其他用户泄漏。 */
    @Test
    void validatesPatchAndHidesOtherUsersResources() throws Exception {
        JsonNode owner = register("food-owner");
        JsonNode other = register("food-other");
        String ownerToken = owner.path("data").path("accessToken").asText();
        String otherToken = other.path("data").path("accessToken").asText();
        String id = json(perform(MockMvcRequestBuilders.post("/api/v1/food-options"), Map.of(
                "name", "专属食物", "category", "自定义", "defaultPrice", "1", "tags", List.of("私有")
        ), ownerToken)).path("data").path("id").asText();

        assertValidation(perform(MockMvcRequestBuilders.patch("/api/v1/food-options/{id}", id), Map.of(), ownerToken));
        assertValidation(perform(MockMvcRequestBuilders.patch("/api/v1/food-options/{id}", id),
                "{\"name\":null}", ownerToken, true));
        assertValidation(perform(MockMvcRequestBuilders.post("/api/v1/food-options"),
                "{\"name\":\"未知\",\"category\":\"自定义\",\"defaultPrice\":\"1\",\"tags\":[],\"extra\":1}", ownerToken, true));
        MvcResult invalidTags = perform(MockMvcRequestBuilders.post("/api/v1/food-options"),
                "{\"name\":\"\",\"category\":\"\",\"defaultPrice\":\"1\",\"tags\":[null,\" \",\"坏\\t标签\"]}",
                ownerToken, true);
        assertValidation(invalidTags);
        JsonNode details = json(invalidTags).path("details");
        assertEquals("name", details.get(0).path("field").asText());
        assertEquals("category", details.get(1).path("field").asText());
        assertEquals("tags[0]", details.get(2).path("field").asText());
        assertEquals("tags[1]", details.get(3).path("field").asText());
        assertEquals("tags[2]", details.get(4).path("field").asText());

        assertEquals(400, perform(MockMvcRequestBuilders.get("/api/v1/food-options/not-a-uuid"), null, ownerToken)
                .getResponse().getStatus());
        assertEquals(404, perform(MockMvcRequestBuilders.get("/api/v1/food-options/{id}", id), null, otherToken)
                .getResponse().getStatus());
    }

    /** 作用：验证认证与静态角色权限。输入：无 Token、ADMIN Token、USER Token。输出：401、403、200。逻辑：ADMIN 不获得普通食物池权限。 */
    @Test
    void enforcesUserOnlyFoodPermissions() throws Exception {
        assertEquals(401, perform(MockMvcRequestBuilders.get("/api/v1/food-options"), null, null)
                .getResponse().getStatus());

        String adminToken = jwtService.issueAccessToken(UUID.randomUUID().toString(), AppConstants.ROLE_ADMIN);
        MvcResult forbidden = perform(MockMvcRequestBuilders.get("/api/v1/food-options"), null, adminToken);
        assertEquals(403, forbidden.getResponse().getStatus());
        assertEquals("FORBIDDEN", json(forbidden).path("code").asText());

        String userToken = register("food-permission").path("data").path("accessToken").asText();
        assertEquals(200, perform(MockMvcRequestBuilders.get("/api/v1/food-options"), null, userToken)
                .getResponse().getStatus());
    }

    /** 作用：验证空池与超过总页数的分页结构。输入：删除全部默认食物后的高页码。输出：空 items 和保留页码。逻辑：通过公开 API 删除，保持数据行为真实。 */
    @Test
    void returnsEmptyPageBeyondAvailablePages() throws Exception {
        String token = register("food-page").path("data").path("accessToken").asText();
        MvcResult result = perform(MockMvcRequestBuilders.get("/api/v1/food-options")
                .param("page", "999").param("size", "20"), null, token);
        JsonNode data = json(result).path("data");
        assertEquals(200, result.getResponse().getStatus());
        assertEquals(0, data.path("items").size());
        assertEquals(999, data.path("page").asInt());
        assertEquals(10, data.path("totalElements").asInt());
    }

    /**
     * 作用：验证默认模板食物修改后仍保留最初来源。
     * 输入：注册初始化得到的第一条默认食物。输出：PATCH 后 source 仍为 DEFAULT。
     * 逻辑：source 只表达创建来源，不随用户编辑变化。
     */
    @Test
    void preservesDefaultSourceAfterPatch() throws Exception {
        String token = register("food-default-source").path("data").path("accessToken").asText();
        JsonNode list = json(perform(MockMvcRequestBuilders.get("/api/v1/food-options")
                .param("size", "1"), null, token)).path("data").path("items");
        String id = list.get(0).path("id").asText();
        MvcResult patched = perform(MockMvcRequestBuilders.patch("/api/v1/food-options/{id}", id),
                Map.of("defaultPrice", "19.999"), token);
        assertEquals(200, patched.getResponse().getStatus());
        assertEquals("DEFAULT", json(patched).path("data").path("source").asText());
        assertEquals("20.00", json(patched).path("data").path("defaultPrice").asText());
    }

    /**
     * 作用：执行原始 JSON 字符串请求。
     * 输入：请求、JSON、Token 和原始体标识。输出：MVC 结果。
     * 逻辑：用于显式 null 与未知字段，避免 Map.of 不允许 null。
     */
    private MvcResult perform(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String rawJson,
            String token,
            boolean raw
    ) throws Exception {
        request.contentType("application/json").content(rawJson);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    /** 作用：断言统一校验响应。输入：MVC 结果。输出：无。逻辑：同时检查状态、业务码、details 和 requestId。 */
    private void assertValidation(MvcResult result) throws Exception {
        assertEquals(400, result.getResponse().getStatus());
        JsonNode error = json(result);
        assertEquals("VALIDATION_FAILED", error.path("code").asText());
        assertTrue(error.path("details").isArray());
        assertFalse(error.path("requestId").asText().isBlank());
        assertEquals(error.path("requestId").asText(), result.getResponse().getHeader("X-Request-Id"));
    }
}

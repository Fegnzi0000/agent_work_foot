package com.hyf.agent_work_foot.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyf.agent_work_foot.food.FoodRequests;
import org.junit.jupiter.api.Test;

/** Food PATCH 字段存在性语义的 Jackson 纯单元测试，不启动 Spring 或数据库。 */
class PatchFieldDeserializationTests {
    private final PatchFieldJacksonConfig configuration = new PatchFieldJacksonConfig();
    private final Module module = configuration.patchFieldModule();
    private final ObjectMapper objectMapper = configuration.objectMapper(module);

    /**
     * 作用：验证缺失字段与显式 null 严格区分。
     * 输入：空对象和 name=null JSON。输出：undefined 与 defined-null。
     * 逻辑：服务据此分别执行保持原值和 400 校验失败。
     */
    @Test
    void distinguishesMissingFieldFromExplicitNull() throws Exception {
        FoodRequests.FoodPatchRequest empty = objectMapper.readValue(
                "{}", FoodRequests.FoodPatchRequest.class
        );
        FoodRequests.FoodPatchRequest explicitNull = objectMapper.readValue(
                "{\"name\":null}", FoodRequests.FoodPatchRequest.class
        );

        assertTrue(empty.empty());
        assertFalse(empty.name().defined());
        assertTrue(explicitNull.name().defined());
        assertNull(explicitNull.name().value());
    }

    /**
     * 作用：验证空标签数组仍属于已定义字段。
     * 输入：tags=[] JSON。输出：defined 空列表。
     * 逻辑：FoodService 将其规范化并保存为默认标签“其他”。
     */
    @Test
    void preservesDefinedEmptyTagArray() throws Exception {
        FoodRequests.FoodPatchRequest request = objectMapper.readValue(
                "{\"tags\":[]}", FoodRequests.FoodPatchRequest.class
        );

        assertTrue(request.tags().defined());
        assertTrue(request.tags().value().isEmpty());
        assertFalse(request.empty());
    }
}

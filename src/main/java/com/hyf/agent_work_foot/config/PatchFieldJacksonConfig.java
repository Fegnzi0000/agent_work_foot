package com.hyf.agent_work_foot.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hyf.agent_work_foot.common.PatchField;
import com.hyf.agent_work_foot.diet.DietRequests;
import com.hyf.agent_work_foot.food.FoodRequests;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** PatchField的Jackson反序列化配置，用于区分字段缺失和显式null。 */
@Configuration
public class PatchFieldJacksonConfig {
    private static final Set<String> FOOD_PATCH_FIELDS = Set.of("name", "category", "defaultPrice", "tags");
    private static final Set<String> DIET_PATCH_FIELDS = Set.of("foodOptionId", "manualFood", "actualPrice", "mealType", "eatenAt");

    /** 作用：注册PatchField模块。输入：无。输出：Jackson Module。逻辑：为包装类型提供上下文泛型解析器。 */
    @Bean
    Module patchFieldModule() {
        SimpleModule module = new SimpleModule("PatchFieldModule");
        module.addDeserializer(PatchField.class, new PatchFieldDeserializer(null));
        module.addDeserializer(FoodRequests.FoodPatchRequest.class, new FoodPatchRequestDeserializer());
        module.addDeserializer(DietRequests.PatchRequest.class, new DietPatchRequestDeserializer());
        return module;
    }

    /**
     * 作用：提供全项目统一 Jackson 2 序列化器。
     * 输入：PatchField 模块。输出：可供 MVC、安全处理器和测试复用的 ObjectMapper。
     * 逻辑：先发现 Java Time 等类路径模块，再注册 PATCH 存在性模块，并保持未知字段严格失败。
     */
    @Bean
    ObjectMapper objectMapper(Module patchFieldModule) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.registerModule(patchFieldModule);
        return objectMapper;
    }

    /**
     * 作用：让 Spring MVC 使用统一 Jackson 2 配置。
     * 输入：公共 ObjectMapper。输出：MVC 扩展器。
     * 逻辑：把严格 JSON 与 Food PATCH 模块对应的转换器放到首位，避免 Boot 4 默认 Jackson 3 转换器抢先处理。
     */
    @Bean
    WebMvcConfigurer jackson2WebMvcConfigurer(ObjectMapper objectMapper) {
        return new WebMvcConfigurer() {
            @Override
            public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
                converters.addFirst(new MappingJackson2HttpMessageConverter(objectMapper));
            }
        };
    }

    /** PatchField上下文反序列化器，字段出现时始终构造defined=true。 */
    private static final class PatchFieldDeserializer extends JsonDeserializer<PatchField<?>> implements ContextualDeserializer {
        private final JavaType valueType;

        /** 作用：创建携带字段泛型的反序列化器。输入：包装值类型。输出：解析器实例。逻辑：由上下文化阶段确定具体类型。 */
        private PatchFieldDeserializer(JavaType valueType) {
            this.valueType = valueType;
        }

        /** 作用：解析已出现的非 null 字段。输入：JSON 解析器和上下文。输出：defined PatchField。逻辑：按字段泛型读取值。 */
        @Override
        public PatchField<?> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return PatchField.of(context.readValue(parser, valueType));
        }

        /** 作用：保留显式 null。输入：反序列化上下文。输出：defined 且值为 null 的字段。逻辑：与缺失字段严格区分。 */
        @Override
        public PatchField<?> getNullValue(DeserializationContext context) {
            return PatchField.of(null);
        }

        /** 作用：解析 PatchField 泛型。输入：上下文和 Bean 属性。输出：携带具体值类型的解析器。逻辑：从属性或上下文类型取第一个泛型。 */
        @Override
        public JsonDeserializer<?> createContextual(DeserializationContext context, BeanProperty property) {
            JavaType wrapper = property == null ? context.getContextualType() : property.getType();
            return new PatchFieldDeserializer(wrapper.containedType(0));
        }
    }

    /** FoodPatchRequest 专用解析器，直接按 JSON 属性存在性构造 PatchField。 */
    private static final class FoodPatchRequestDeserializer extends JsonDeserializer<FoodRequests.FoodPatchRequest> {
        /**
         * 作用：解析完整 Food PATCH 请求。
         * 输入：JSON 对象与反序列化上下文。输出：保留缺失/null/有效值三态的请求。
         * 逻辑：先拒绝未知字段和错误类型，再按节点是否存在构造各 PatchField。
         */
        @Override
        public FoodRequests.FoodPatchRequest deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            JsonNode root = parser.readValueAsTree();
            if (!root.isObject()) {
                return (FoodRequests.FoodPatchRequest) context.handleUnexpectedToken(
                        FoodRequests.FoodPatchRequest.class, parser
                );
            }
            Iterator<String> names = root.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (!FOOD_PATCH_FIELDS.contains(name)) {
                    context.reportInputMismatch(FoodRequests.FoodPatchRequest.class, "未知字段: %s", name);
                }
            }
            return new FoodRequests.FoodPatchRequest(
                    stringField(root, "name", context),
                    stringField(root, "category", context),
                    stringField(root, "defaultPrice", context),
                    tagsField(root, context)
            );
        }

        /** 作用：解析可选字符串字段。输入：根节点、字段名和上下文。输出：三态 PatchField。逻辑：拒绝非字符串非 null 值。 */
        private PatchField<String> stringField(JsonNode root, String name, DeserializationContext context)
                throws IOException {
            if (!root.has(name)) {
                return PatchField.undefined();
            }
            JsonNode value = root.get(name);
            if (value.isNull()) {
                return PatchField.of(null);
            }
            if (!value.isTextual()) {
                context.reportInputMismatch(FoodRequests.FoodPatchRequest.class, "%s 必须是字符串", name);
            }
            return PatchField.of(value.textValue());
        }

        /** 作用：解析可选标签数组。输入：根节点和上下文。输出：三态列表字段。逻辑：保留数组 null 项供字段级内容校验。 */
        private PatchField<List<String>> tagsField(JsonNode root, DeserializationContext context)
                throws IOException {
            if (!root.has("tags")) {
                return PatchField.undefined();
            }
            JsonNode value = root.get("tags");
            if (value.isNull()) {
                return PatchField.of(null);
            }
            if (!value.isArray()) {
                context.reportInputMismatch(FoodRequests.FoodPatchRequest.class, "tags 必须是数组");
            }
            List<String> tags = new ArrayList<>();
            for (JsonNode item : value) {
                if (item.isNull()) {
                    tags.add(null);
                } else if (item.isTextual()) {
                    tags.add(item.textValue());
                } else {
                    context.reportInputMismatch(FoodRequests.FoodPatchRequest.class, "tags 数组项必须是字符串或null");
                }
            }
            return PatchField.of(Collections.unmodifiableList(tags));
        }
    }

    /** Diet PATCH 专用解析器，保证食物二选一字段可区分缺失、null与有效值。 */
    private static final class DietPatchRequestDeserializer extends JsonDeserializer<DietRequests.PatchRequest> {
        @Override
        public DietRequests.PatchRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode root = parser.readValueAsTree();
            if (!root.isObject()) return (DietRequests.PatchRequest) context.handleUnexpectedToken(DietRequests.PatchRequest.class, parser);
            Iterator<String> names = root.fieldNames();
            while (names.hasNext()) { String name = names.next(); if (!DIET_PATCH_FIELDS.contains(name)) context.reportInputMismatch(DietRequests.PatchRequest.class, "未知字段: %s", name); }
            return new DietRequests.PatchRequest(string(root, "foodOptionId", context), manual(root, context),
                    string(root, "actualPrice", context), string(root, "mealType", context), time(root, context));
        }

        private PatchField<String> string(JsonNode root, String name, DeserializationContext context) throws IOException {
            if (!root.has(name)) return PatchField.undefined(); JsonNode node = root.get(name);
            if (node.isNull()) return PatchField.of(null);
            if (!node.isTextual()) context.reportInputMismatch(DietRequests.PatchRequest.class, "%s 必须是字符串", name);
            return PatchField.of(node.textValue());
        }

        private PatchField<DietRequests.ManualFood> manual(JsonNode root, DeserializationContext context) throws IOException {
            if (!root.has("manualFood")) return PatchField.undefined(); JsonNode node = root.get("manualFood");
            if (node.isNull()) return PatchField.of(null);
            if (!node.isObject()) context.reportInputMismatch(DietRequests.PatchRequest.class, "manualFood 必须是对象");
            return PatchField.of(context.readTreeAsValue(node, DietRequests.ManualFood.class));
        }

        private PatchField<java.time.OffsetDateTime> time(JsonNode root, DeserializationContext context) throws IOException {
            if (!root.has("eatenAt")) return PatchField.undefined(); JsonNode node = root.get("eatenAt");
            if (node.isNull()) return PatchField.of(null);
            if (!node.isTextual()) context.reportInputMismatch(DietRequests.PatchRequest.class, "eatenAt 必须是带时区的时间字符串");
            try { return PatchField.of(java.time.OffsetDateTime.parse(node.textValue())); }
            catch (java.time.format.DateTimeParseException exception) { context.reportInputMismatch(DietRequests.PatchRequest.class, "eatenAt 格式不正确"); return PatchField.undefined(); }
        }
    }
}

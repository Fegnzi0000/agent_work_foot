package com.hyf.agent_work_foot.food;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hyf.agent_work_foot.common.PatchField;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** food接口请求DTO集合；跨字段、码点和金额规则由FoodService统一处理。 */
public final class FoodRequests {
    private FoodRequests() { }

    /** 创建食物请求，四个字段均必须出现，tags可为空数组。 */
    public record FoodWriteRequest(@NotNull String name, @NotNull String category,
                                   @NotNull String defaultPrice, @NotNull List<String> tags) { }

    /** PATCH请求，通过JsonCreator读取字段是否出现并保留显式null。 */
    public static final class FoodPatchRequest {
        private final PatchField<String> name;
        private final PatchField<String> category;
        private final PatchField<String> defaultPrice;
        private final PatchField<List<String>> tags;

        /**
         * 作用：构造PATCH字段状态。输入：Jackson注入的可空字段。输出：不可变请求。
         * 逻辑：本构造器由自定义反序列化器调用，普通属性构造不用于判定字段缺失。
         */
        @JsonCreator
        public FoodPatchRequest(@JsonProperty("name") PatchField<String> name,
                                @JsonProperty("category") PatchField<String> category,
                                @JsonProperty("defaultPrice") PatchField<String> defaultPrice,
                                @JsonProperty("tags") PatchField<List<String>> tags) {
            this.name = name == null ? PatchField.undefined() : name;
            this.category = category == null ? PatchField.undefined() : category;
            this.defaultPrice = defaultPrice == null ? PatchField.undefined() : defaultPrice;
            this.tags = tags == null ? PatchField.undefined() : tags;
        }

        /** 作用：读取名称字段状态。输入：无。输出：缺失、null 或具体值。逻辑：不改变请求状态。 */
        public PatchField<String> name() { return name; }

        /** 作用：读取分类字段状态。输入：无。输出：缺失、null 或具体值。逻辑：不改变请求状态。 */
        public PatchField<String> category() { return category; }

        /** 作用：读取价格字段状态。输入：无。输出：缺失、null 或具体值。逻辑：不改变请求状态。 */
        public PatchField<String> defaultPrice() { return defaultPrice; }

        /** 作用：读取标签字段状态。输入：无。输出：缺失、null 或列表。逻辑：空列表仍属于已定义字段。 */
        public PatchField<List<String>> tags() { return tags; }

        /** 作用：判断 PATCH 是否为空对象。输入：无。输出：是否所有字段都缺失。逻辑：显式 null 不视为空对象。 */
        public boolean empty() { return !name.defined() && !category.defined() && !defaultPrice.defined() && !tags.defined(); }
    }
}

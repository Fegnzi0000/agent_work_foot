package com.hyf.agent_work_foot.diet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.hyf.agent_work_foot.common.PatchField;
import com.hyf.agent_work_foot.common.StrictStringDeserializer;
import java.time.OffsetDateTime;
import java.util.List;

/** Diet HTTP 请求DTO；跨字段互斥、金额、时间和快照规则由DietService处理。 */
public final class DietRequests {
    private DietRequests() { }

    /** 创建记录的手工食物输入；名称和分类必填，tags可为空数组。 */
    public record ManualFood(String name, String category, List<String> tags) { }

    /** 创建饮食记录请求；foodOptionId与manualFood严格二选一。 */
    public record CreateRequest(String foodOptionId, ManualFood manualFood, Boolean addToFoodPool,
                                @JsonDeserialize(using = StrictStringDeserializer.class) String actualPrice,
                                String mealType, OffsetDateTime eatenAt) { }

    /** PATCH请求的三态字段模型；自定义反序列化器负责保留缺失与显式null。 */
    public static final class PatchRequest {
        private final PatchField<String> foodOptionId;
        private final PatchField<ManualFood> manualFood;
        private final PatchField<String> actualPrice;
        private final PatchField<String> mealType;
        private final PatchField<OffsetDateTime> eatenAt;

        @JsonCreator
        public PatchRequest(@JsonProperty("foodOptionId") PatchField<String> foodOptionId,
                            @JsonProperty("manualFood") PatchField<ManualFood> manualFood,
                            @JsonProperty("actualPrice") PatchField<String> actualPrice,
                            @JsonProperty("mealType") PatchField<String> mealType,
                            @JsonProperty("eatenAt") PatchField<OffsetDateTime> eatenAt) {
            this.foodOptionId = foodOptionId == null ? PatchField.undefined() : foodOptionId;
            this.manualFood = manualFood == null ? PatchField.undefined() : manualFood;
            this.actualPrice = actualPrice == null ? PatchField.undefined() : actualPrice;
            this.mealType = mealType == null ? PatchField.undefined() : mealType;
            this.eatenAt = eatenAt == null ? PatchField.undefined() : eatenAt;
        }

        public PatchField<String> foodOptionId() { return foodOptionId; }
        public PatchField<ManualFood> manualFood() { return manualFood; }
        public PatchField<String> actualPrice() { return actualPrice; }
        public PatchField<String> mealType() { return mealType; }
        public PatchField<OffsetDateTime> eatenAt() { return eatenAt; }
        public boolean empty() { return !foodOptionId.defined() && !manualFood.defined() && !actualPrice.defined()
                && !mealType.defined() && !eatenAt.defined(); }
    }
}

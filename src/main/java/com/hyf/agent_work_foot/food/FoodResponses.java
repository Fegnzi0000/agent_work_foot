package com.hyf.agent_work_foot.food;

import java.util.List;

/** food接口响应DTO集合，仅暴露OpenAPI约定字段。 */
public final class FoodResponses {
    private FoodResponses() { }

    /** 单个食物的六字段公开响应。 */
    public record FoodOptionData(String id, String name, String category, String defaultPrice,
                                 List<String> tags, String source) { }

    /** 食物分页响应，page保持外部0基语义。 */
    public record FoodPageData(List<FoodOptionData> items, int page, int size,
                               long totalElements, long totalPages) { }
}

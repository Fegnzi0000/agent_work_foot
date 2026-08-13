package com.hyf.agent_work_foot.food.mapper.model;

/** 批量标签查询投影，携带食物ID用于Service分组。 */
public record FoodTagRow(String id, String foodOptionId, String tag, String normalizedTag) {
}

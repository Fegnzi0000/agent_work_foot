package com.hyf.agent_work_foot.food.mapper.model;

import java.math.BigDecimal;

/** 复杂分页查询返回的食物主表投影，不依赖HTTP响应模型。 */
public record FoodPageRow(String id, String name, String category, BigDecimal defaultPrice, String source) {
}

package com.hyf.agent_work_foot.diet.mapper.model;

import java.math.BigDecimal;

/** 分类消费聚合投影，空分类在SQL中统一映射为未分类。 */
public record DietCategoryRow(String category, BigDecimal totalSpent, long recordCount) {
}

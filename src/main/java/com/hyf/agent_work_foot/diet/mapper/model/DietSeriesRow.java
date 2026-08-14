package com.hyf.agent_work_foot.diet.mapper.model;

import java.math.BigDecimal;

/** 消费趋势聚合投影，period由数据库按请求粒度格式化。 */
public record DietSeriesRow(String period, BigDecimal totalSpent) {
}

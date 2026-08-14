package com.hyf.agent_work_foot.diet.mapper.model;

import java.math.BigDecimal;

/** 指定业务日期范围的整体消费汇总投影。 */
public record DietSummaryRow(BigDecimal totalSpent, long recordCount, long recordedDays) {
}

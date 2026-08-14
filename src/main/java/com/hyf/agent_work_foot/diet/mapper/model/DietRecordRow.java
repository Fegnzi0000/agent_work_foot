package com.hyf.agent_work_foot.diet.mapper.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 饮食记录列表和详情使用的数据库投影，仅在diet模块内部传递。 */
public record DietRecordRow(String id, String foodOptionId, String foodNameSnapshot, String categorySnapshot,
                            List<String> tagsSnapshotJson, BigDecimal actualPrice, String mealType,
                            LocalDateTime eatenAt, LocalDate businessDate, String source,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
}

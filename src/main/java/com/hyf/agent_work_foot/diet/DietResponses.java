package com.hyf.agent_work_foot.diet;

import java.util.List;

/** Diet 对外响应DTO；金额以固定两位十进制字符串返回。 */
public final class DietResponses {
    private DietResponses() { }

    public record DietRecordData(String id, String foodOptionId, String foodName, String category, List<String> tags,
                                 String actualPrice, String mealType, String eatenAt, String businessDate,
                                 String source, String createdAt, String updatedAt) { }
    public record DietRecordPageData(List<DietRecordData> items, int page, int size, long totalElements,
                                     long totalPages) { }
    public record SpendingPoint(String period, String totalSpent) { }
    public record CategoryDistribution(String category, String totalSpent, long recordCount) { }
    public record DietStatisticsData(String totalSpent, long recordCount, long recordedDays, String averageDailySpent,
                                     List<SpendingPoint> spendingSeries,
                                     List<CategoryDistribution> categoryDistribution) { }
}

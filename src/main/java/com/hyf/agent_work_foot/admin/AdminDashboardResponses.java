package com.hyf.agent_work_foot.admin;

import java.util.List;

/** 管理员Dashboard响应DTO；日期均为Asia/Shanghai自然日。 */
public final class AdminDashboardResponses {
    private AdminDashboardResponses() {
    }

    public record DashboardData(Range range, Summary summary, List<DailyPoint> dailySeries,
                                List<RecordSourceDistribution> recordSourceDistribution) {
    }

    public record Range(String startDate, String endDate, String timezone) {
    }

    public record Summary(long totalUsers, long todayNewUsers, long activeUsers, long dietRecords,
                          long slotSpins, long slotConfirmed, String slotConfirmationRate) {
    }

    public record DailyPoint(String date, long newUsers, long activeUsers, long dietRecords) {
    }

    public record RecordSourceDistribution(String source, long count, String percentage) {
    }
}

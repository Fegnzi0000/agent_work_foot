package com.hyf.agent_work_foot.admin.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 管理员看板聚合查询，所有统计仅覆盖当前角色为USER的账号。 */
public interface AdminDashboardMapper {
    DashboardSummaryRow selectSummary(
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd,
            @Param("todayStart") LocalDateTime todayStart,
            @Param("tomorrowStart") LocalDateTime tomorrowStart,
            @Param("businessStart") LocalDate businessStart,
            @Param("businessEnd") LocalDate businessEnd
    );

    List<DashboardDailyRow> selectDailyNewUsers(
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd
    );

    List<DashboardDailyRow> selectDailyActiveUsers(
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd
    );

    List<DashboardDailyRow> selectDailyDietRecords(
            @Param("businessStart") LocalDate businessStart,
            @Param("businessEnd") LocalDate businessEnd
    );

    List<DashboardSourceRow> selectDietRecordSources(
            @Param("businessStart") LocalDate businessStart,
            @Param("businessEnd") LocalDate businessEnd
    );

    record DashboardSummaryRow(long totalUsers, long todayNewUsers, long activeUsers,
                               long dietRecords, long slotSpins, long slotConfirmed) {
    }

    record DashboardDailyRow(String date, long count) {
    }

    record DashboardSourceRow(String source, long count) {
    }
}

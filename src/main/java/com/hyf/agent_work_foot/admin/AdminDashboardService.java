package com.hyf.agent_work_foot.admin;

import com.hyf.agent_work_foot.admin.mapper.AdminDashboardMapper;
import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.common.FieldErrorDetail;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 管理员Dashboard应用服务：统一以北京时间界定自然日，再以UTC边界查询数据库。 */
@Service
public class AdminDashboardService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int DEFAULT_RANGE_DAYS = 7;
    private static final int MAX_RANGE_DAYS = 90;

    private final AdminDashboardMapper mapper;
    private final Clock clock;

    public AdminDashboardService(AdminDashboardMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    public AdminDashboardResponses.DashboardData dashboard(LocalDate requestedStart, LocalDate requestedEnd) {
        DateRange range = resolveRange(requestedStart, requestedEnd);
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        LocalDateTime rangeStart = utcDateTime(range.start());
        LocalDateTime rangeEnd = utcDateTime(range.end().plusDays(1));
        LocalDateTime todayStart = utcDateTime(today);
        LocalDateTime tomorrowStart = utcDateTime(today.plusDays(1));

        AdminDashboardMapper.DashboardSummaryRow summary = mapper.selectSummary(
                rangeStart, rangeEnd, todayStart, tomorrowStart, range.start(), range.end()
        );
        Map<String, Long> newUsers = toDailyCountMap(mapper.selectDailyNewUsers(rangeStart, rangeEnd));
        Map<String, Long> activeUsers = toDailyCountMap(mapper.selectDailyActiveUsers(rangeStart, rangeEnd));
        Map<String, Long> dietRecords = toDailyCountMap(mapper.selectDailyDietRecords(range.start(), range.end()));

        List<AdminDashboardResponses.DailyPoint> dailySeries = range.start().datesUntil(range.end().plusDays(1))
                .map(date -> {
                    String key = date.toString();
                    return new AdminDashboardResponses.DailyPoint(key, newUsers.getOrDefault(key, 0L),
                            activeUsers.getOrDefault(key, 0L), dietRecords.getOrDefault(key, 0L));
                })
                .toList();
        List<AdminDashboardResponses.RecordSourceDistribution> sources = sourceDistribution(
                mapper.selectDietRecordSources(range.start(), range.end()), summary.dietRecords()
        );

        return new AdminDashboardResponses.DashboardData(
                new AdminDashboardResponses.Range(range.start().toString(), range.end().toString(), BUSINESS_ZONE.getId()),
                new AdminDashboardResponses.Summary(summary.totalUsers(), summary.todayNewUsers(), summary.activeUsers(),
                        summary.dietRecords(), summary.slotSpins(), summary.slotConfirmed(),
                        percentage(summary.slotConfirmed(), summary.slotSpins())),
                dailySeries, sources
        );
    }

    private DateRange resolveRange(LocalDate start, LocalDate end) {
        if (start == null && end == null) {
            LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
            return new DateRange(today.minusDays(DEFAULT_RANGE_DAYS - 1L), today);
        }
        if (start == null || end == null) {
            throw validation("startDate与endDate必须同时传递");
        }
        if (start.isAfter(end)) {
            throw validation("startDate不能晚于endDate");
        }
        if (start.plusDays(MAX_RANGE_DAYS - 1L).isBefore(end)) {
            throw validation("日期范围最多90天（含首尾）");
        }
        return new DateRange(start, end);
    }

    private List<AdminDashboardResponses.RecordSourceDistribution> sourceDistribution(
            List<AdminDashboardMapper.DashboardSourceRow> rows, long totalDietRecords
    ) {
        Map<String, Long> counts = toSourceCountMap(rows);
        return List.of("MANUAL", "SLOT").stream()
                .map(source -> new AdminDashboardResponses.RecordSourceDistribution(
                        source, counts.getOrDefault(source, 0L), percentage(counts.getOrDefault(source, 0L), totalDietRecords)
                )).toList();
    }

    private Map<String, Long> toDailyCountMap(List<AdminDashboardMapper.DashboardDailyRow> rows) {
        Map<String, Long> result = new HashMap<>();
        rows.forEach(row -> result.put(row.date(), row.count()));
        return result;
    }

    private Map<String, Long> toSourceCountMap(List<AdminDashboardMapper.DashboardSourceRow> rows) {
        Map<String, Long> result = new HashMap<>();
        rows.forEach(row -> result.put(row.source(), row.count()));
        return result;
    }

    private String percentage(long numerator, long denominator) {
        if (denominator == 0) {
            return "0.00";
        }
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP).toPlainString();
    }

    private LocalDateTime utcDateTime(LocalDate date) {
        Instant instant = date.atStartOfDay(BUSINESS_ZONE).toInstant();
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private ApiException validation(String reason) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数不合法",
                List.of(new FieldErrorDetail("dateRange", reason)));
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }
}

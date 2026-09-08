package com.hyf.agent_work_foot.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hyf.agent_work_foot.admin.mapper.AdminDashboardMapper;
import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.config.RedisCacheProperties;
import com.hyf.agent_work_foot.config.RedisJsonCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.mockito.Mockito.mock;

/** Dashboard日期边界、零值补齐和比例格式的无数据库单元测试。 */
class AdminDashboardServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-25T08:00:00Z"), java.time.ZoneOffset.UTC);

    @Test
    void defaultsToSevenShanghaiDaysAndFillsMissingDailyValues() {
        AdminDashboardService service = new AdminDashboardService(new StubMapper(), clock, cache(), cacheProperties());

        AdminDashboardResponses.DashboardData result = service.dashboard(null, null);

        assertEquals("2026-08-19", result.range().startDate());
        assertEquals("2026-08-25", result.range().endDate());
        assertEquals(7, result.dailySeries().size());
        assertEquals(2, result.dailySeries().get(0).newUsers());
        assertEquals(0, result.dailySeries().get(1).dietRecords());
        assertEquals("50.00", result.summary().slotConfirmationRate());
        assertEquals("0.00", result.recordSourceDistribution().get(1).percentage());
    }

    @Test
    void rejectsIncompleteOrOversizedDateRange() {
        AdminDashboardService service = new AdminDashboardService(new StubMapper(), clock, cache(), cacheProperties());

        assertEquals("VALIDATION_FAILED", assertThrows(ApiException.class,
                () -> service.dashboard(LocalDate.of(2026, 8, 1), null)).code());
        assertEquals("VALIDATION_FAILED", assertThrows(ApiException.class,
                () -> service.dashboard(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1))).code());
    }

    private RedisJsonCache cache() {
        return new RedisJsonCache(mock(StringRedisTemplate.class), new ObjectMapper(), cacheProperties());
    }

    private RedisCacheProperties cacheProperties() {
        return new RedisCacheProperties(false, java.time.Duration.ofMinutes(5), java.time.Duration.ofHours(6), java.time.Duration.ofSeconds(30));
    }

    private static class StubMapper implements AdminDashboardMapper {
        @Override
        public DashboardSummaryRow selectSummary(LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                                 LocalDateTime todayStart, LocalDateTime tomorrowStart,
                                                 LocalDate businessStart, LocalDate businessEnd) {
            return new DashboardSummaryRow(8, 1, 3, 4, 2, 1);
        }

        @Override
        public List<DashboardDailyRow> selectDailyNewUsers(LocalDateTime rangeStart, LocalDateTime rangeEnd) {
            return List.of(new DashboardDailyRow("2026-08-19", 2));
        }

        @Override
        public List<DashboardDailyRow> selectDailyActiveUsers(LocalDateTime rangeStart, LocalDateTime rangeEnd) {
            return List.of(new DashboardDailyRow("2026-08-25", 3));
        }

        @Override
        public List<DashboardDailyRow> selectDailyDietRecords(LocalDate businessStart, LocalDate businessEnd) {
            return List.of(new DashboardDailyRow("2026-08-19", 4));
        }

        @Override
        public List<DashboardSourceRow> selectDietRecordSources(LocalDate businessStart, LocalDate businessEnd) {
            return List.of(new DashboardSourceRow("MANUAL", 4));
        }
    }
}

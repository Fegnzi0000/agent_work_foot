package com.hyf.agent_work_foot.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyf.agent_work_foot.admin.mapper.AdminAuditMapper;
import com.hyf.agent_work_foot.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 审计查询的默认日期、摘要白名单和参数校验测试，不依赖MySQL或Docker。 */
class AdminAuditLogServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-26T03:00:00Z"), java.time.ZoneOffset.UTC);

    @Test
    void defaultsToThirtyShanghaiDaysAndOnlyReturnsWhitelistedDetail() {
        AdminAuditLogService service = new AdminAuditLogService(new StubMapper(), new ObjectMapper(), clock);

        AdminResponses.AdminAuditLogPageData result = service.list(
                null, null, "user_disabled", "success", null, null, 0, 20
        );

        assertEquals(1, result.items().size());
        assertEquals("admin@example.com", result.items().getFirst().admin().email());
        assertEquals("ACTIVE", result.items().getFirst().detail().get("before"));
        assertEquals("DISABLED", result.items().getFirst().detail().get("after"));
        assertFalse(result.items().getFirst().detail().containsKey("password"));
        assertEquals(1, result.totalPages());
    }

    @Test
    void rejectsInvalidFiltersAndDateRange() {
        AdminAuditLogService service = new AdminAuditLogService(new StubMapper(), new ObjectMapper(), clock);

        assertEquals("VALIDATION_FAILED", assertThrows(ApiException.class,
                () -> service.list("not-a-uuid", null, null, null, null, null, 0, 20)).code());
        assertEquals("VALIDATION_FAILED", assertThrows(ApiException.class,
                () -> service.list(null, null, "unknown", null, null, null, 0, 20)).code());
        assertEquals("VALIDATION_FAILED", assertThrows(ApiException.class,
                () -> service.list(null, null, null, null, LocalDate.of(2026, 8, 1), null, 0, 20)).code());
    }

    private static class StubMapper implements AdminAuditMapper {
        @Override
        public void insertAudit(AdminAuditInsert audit) {
        }

        @Override
        public long countByTargetAndAction(String targetUserId, String action) {
            return 0;
        }

        @Override
        public List<AdminAuditLogRow> selectAuditLogPage(AdminAuditLogQuery query) {
            return List.of(new AdminAuditLogRow(
                    "audit-1", "00000000-0000-4000-8000-000000000001", "admin@example.com", "管理员",
                    "00000000-0000-4000-8000-000000000002", "user@example.com", "用户",
                    "USER_DISABLED", "SUCCESS", "request-1",
                    "{\"before\":\"ACTIVE\",\"after\":\"DISABLED\",\"password\":\"forbidden\"}",
                    Instant.parse("2026-08-26T01:00:00Z")
            ));
        }

        @Override
        public long countAuditLogs(AdminAuditLogQuery query) {
            return 1;
        }
    }
}

package com.hyf.agent_work_foot.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyf.agent_work_foot.admin.mapper.AdminAuditMapper;
import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.common.FieldErrorDetail;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 管理员审计查询服务：仅输出账号识别信息和经过白名单过滤的操作摘要。 */
@Service
public class AdminAuditLogService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int DEFAULT_RANGE_DAYS = 30;
    private static final int MAX_RANGE_DAYS = 90;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ACTIONS = Set.of(
            "USER_DISABLED", "USER_ENABLED", "USER_STATUS_UNCHANGED", "TEMP_PASSWORD_CREATED",
            "USER_STATUS_UPDATE", "TEMP_PASSWORD_CREATE"
    );
    private static final Set<String> RESULTS = Set.of("SUCCESS", "FAILURE");
    private static final List<String> SAFE_DETAIL_FIELDS = List.of("before", "after", "reason", "expiresAt");

    private final AdminAuditMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AdminAuditLogService(AdminAuditMapper mapper, ObjectMapper objectMapper, Clock clock) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public AdminResponses.AdminAuditLogPageData list(String adminUserId, String targetUserId, String action,
                                                      String result, LocalDate startDate, LocalDate endDate,
                                                      int page, int size) {
        DateRange range = resolveRange(startDate, endDate);
        validatePage(page, size);
        AdminAuditMapper.AdminAuditLogQuery query = new AdminAuditMapper.AdminAuditLogQuery(
                normalizeUuid(adminUserId, "adminUserId"), normalizeUuid(targetUserId, "targetUserId"),
                normalizeEnum(action, ACTIONS, "action"), normalizeEnum(result, RESULTS, "result"),
                utcDateTime(range.start()), utcDateTime(range.end().plusDays(1)), page * size, size
        );
        long total = mapper.countAuditLogs(query);
        List<AdminResponses.AdminAuditLogData> items = mapper.selectAuditLogPage(query).stream()
                .map(this::response).toList();
        return new AdminResponses.AdminAuditLogPageData(items, page, size, total, totalPages(total, size));
    }

    private AdminResponses.AdminAuditLogData response(AdminAuditMapper.AdminAuditLogRow row) {
        return new AdminResponses.AdminAuditLogData(
                row.id(), new AdminResponses.AuditAccountData(row.adminUserId(), row.adminEmail(), row.adminNickname()),
                row.targetUserId() == null ? null : new AdminResponses.AuditAccountData(
                        row.targetUserId(), row.targetEmail(), row.targetNickname()),
                row.action(), row.result(), row.requestId(), safeDetail(row.detailJson()), row.createdAt()
        );
    }

    private Map<String, String> safeDetail(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode detail = objectMapper.readTree(detailJson);
            Map<String, String> safe = new LinkedHashMap<>();
            for (String field : SAFE_DETAIL_FIELDS) {
                JsonNode value = detail.get(field);
                if (value != null && value.isValueNode()) {
                    safe.put(field, value.asText());
                }
            }
            return Map.copyOf(safe);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("审计摘要数据格式错误", exception);
        }
    }

    private DateRange resolveRange(LocalDate start, LocalDate end) {
        if (start == null && end == null) {
            LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
            return new DateRange(today.minusDays(DEFAULT_RANGE_DAYS - 1L), today);
        }
        if (start == null || end == null) {
            throw validation("dateRange", "startDate与endDate必须同时传递");
        }
        if (start.isAfter(end)) {
            throw validation("dateRange", "startDate不能晚于endDate");
        }
        if (start.plusDays(MAX_RANGE_DAYS - 1L).isBefore(end)) {
            throw validation("dateRange", "日期范围最多90天（含首尾）");
        }
        return new DateRange(start, end);
    }

    private String normalizeUuid(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim()).toString();
        } catch (IllegalArgumentException exception) {
            throw validation(field, "必须是UUID");
        }
    }

    private String normalizeEnum(String value, Set<String> allowed, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw validation(field, "参数值不合法");
        }
        return normalized;
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || page > Integer.MAX_VALUE / size) {
            throw validation("page", "page必须不小于0，size必须在1至100之间");
        }
    }

    private long totalPages(long total, int size) {
        return total == 0 ? 0 : (total + size - 1) / size;
    }

    private LocalDateTime utcDateTime(LocalDate date) {
        Instant instant = date.atStartOfDay(BUSINESS_ZONE).toInstant();
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private ApiException validation(String field, String reason) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数不合法",
                List.of(new FieldErrorDetail(field, reason)));
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }
}

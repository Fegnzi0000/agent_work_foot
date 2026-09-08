package com.hyf.agent_work_foot.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import com.hyf.agent_work_foot.common.ApiException;
import java.util.UUID;

/** Current agreement state; implemented against append-only migration V4. */
@Service
public class ConsentService {
    public static final String VERSION = "2026-09-06.1";
    private final JdbcTemplate jdbc;
    public ConsentService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public static void validate(AuthRequests.WeChatMiniProgramLoginRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.accepted())
                || !VERSION.equals(request.termsVersion()) || !VERSION.equals(request.privacyVersion())
                || !("ADULT".equals(request.ageBand()) || "AGE_14_17".equals(request.ageBand()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONSENT_REQUIRED", "请主动同意当前协议并确认允许的年龄范围");
        }
    }
    public State state(String id) {
        var rows = jdbc.query("SELECT terms_version,privacy_version,age_band FROM user_consents WHERE user_id=?",
                (rs, n) -> new State(VERSION.equals(rs.getString(1)) && VERSION.equals(rs.getString(2)), rs.getString(3)), id);
        return rows.isEmpty() ? new State(false, null) : rows.getFirst();
    }
    /** 登录同意写入通过用户行锁串行化，避免并发登录覆盖年龄段。 */
    @Transactional
    public void acceptLogin(String id, AuthRequests.WeChatMiniProgramLoginRequest request) {
        validate(request);
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", String.class, id);
        var previous = state(id);
        if ("AGE_14_17".equals(previous.ageBand()) && "ADULT".equals(request.ageBand())) {
            throw new ApiException(HttpStatus.CONFLICT, "AGE_CORRECTION_REQUIRED", "年龄段更正请联系开发者核验，不能通过重新登录绕过年龄限制");
        }
        jdbc.update("INSERT INTO user_consents(user_id,terms_version,privacy_version,age_band,accepted_at) VALUES(?,?,?,?,UTC_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE terms_version=VALUES(terms_version),privacy_version=VALUES(privacy_version),age_band=VALUES(age_band),accepted_at=VALUES(accepted_at)", id, VERSION, VERSION, request.ageBand());
        event(id, "LOGIN", true);
    }
    private void event(String id, String kind, boolean accepted) {
        jdbc.update("INSERT INTO consent_events(id,user_id,kind,version,accepted,created_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(3))", UUID.randomUUID().toString(), id, kind, VERSION, accepted);
    }
    public record State(boolean current, String ageBand) { }
}

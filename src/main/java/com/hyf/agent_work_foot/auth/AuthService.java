package com.hyf.agent_work_foot.auth;

import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthProperties properties;

    public AuthService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, JwtService jwtService,
                       AuthProperties properties) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @Transactional
    public AuthResponses.AuthData register(AuthRequests.RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_CONFIRMATION_MISMATCH", "两次密码输入不一致");
        }
        String email = normalizeEmail(request.email());
        if (jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "邮箱已注册");
        }

        UserRow user = new UserRow(UUID.randomUUID().toString(), email, defaultNickname(email), null,
                "USER", "ACTIVE", false, false);
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, nickname, role, status, onboarding_completed, must_change_password)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, user.id(), user.email(), passwordEncoder.encode(request.password()), user.nickname(), user.role(),
                user.status(), user.onboardingCompleted(), user.mustChangePassword());
        copyDefaultFoods(user.id());
        return toAuthData(user, issueRefreshToken(user.id(), null));
    }

    public AuthResponses.AuthData login(AuthRequests.LoginRequest request) {
        UserRow user = findUserByEmail(normalizeEmail(request.email()));
        if (user == null || !"ACTIVE".equals(user.status()) || !passwordMatches(request.password(), user.id())) {
            throw unauthorized();
        }
        jdbc.update("UPDATE users SET last_login_at = CURRENT_TIMESTAMP(3) WHERE id = ?", user.id());
        return toAuthData(user, issueRefreshToken(user.id(), null));
    }

    @Transactional
    public AuthResponses.TokenData refresh(String rawRefreshToken) {
        RefreshTokenRow token = findRefreshToken(rawRefreshToken);
        if (token == null || token.revokedAt() != null || !token.expiresAt().isAfter(Instant.now()) || !"ACTIVE".equals(token.status())) {
            throw unauthorized();
        }
        jdbc.update("UPDATE refresh_tokens SET revoked_at = CURRENT_TIMESTAMP(3), revoke_reason = 'ROTATED' WHERE id = ?", token.id());
        RefreshTokenPair refreshed = issueRefreshToken(token.userId(), token.id());
        return new AuthResponses.TokenData(jwtService.issueAccessToken(token.userId(), token.role()),
                properties.jwt().accessTokenTtl().toSeconds(), refreshed.rawToken(), properties.jwt().refreshTokenTtl().toSeconds());
    }

    public void logout(String rawRefreshToken) {
        int updated = jdbc.update("""
                UPDATE refresh_tokens
                SET revoked_at = CURRENT_TIMESTAMP(3), revoke_reason = 'LOGOUT'
                WHERE token_hash = ? AND revoked_at IS NULL
                """, hash(rawRefreshToken));
        if (updated == 0) throw unauthorized();
    }

    private AuthResponses.AuthData toAuthData(UserRow user, RefreshTokenPair refreshToken) {
        return new AuthResponses.AuthData(jwtService.issueAccessToken(user.id(), user.role()),
                properties.jwt().accessTokenTtl().toSeconds(), refreshToken.rawToken(), properties.jwt().refreshTokenTtl().toSeconds(),
                new AuthResponses.UserData(user.id(), user.email(), user.nickname(), user.avatarObjectKey(), user.role(),
                        user.status(), user.onboardingCompleted(), user.mustChangePassword()), nextStep(user));
    }

    private RefreshTokenPair issueRefreshToken(String userId, String parentTokenId) {
        byte[] value = new byte[48];
        RANDOM.nextBytes(value);
        String rawToken = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        jdbc.update("""
                INSERT INTO refresh_tokens (id, user_id, token_hash, parent_token_id, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), userId, hash(rawToken), parentTokenId,
                java.sql.Timestamp.from(Instant.now().plus(properties.jwt().refreshTokenTtl())));
        return new RefreshTokenPair(rawToken);
    }

    private void copyDefaultFoods(String userId) {
        List<TemplateRow> templates = jdbc.query("""
                SELECT name, normalized_name, category, default_price, tags_json
                FROM food_default_templates WHERE is_active = 1 ORDER BY sort_order
                """, (rs, rowNum) -> new TemplateRow(rs.getString("name"), rs.getString("normalized_name"),
                rs.getString("category"), rs.getBigDecimal("default_price"), rs.getString("tags_json")));
        for (TemplateRow template : templates) {
            String foodId = UUID.randomUUID().toString();
            jdbc.update("""
                    INSERT INTO food_options (id, user_id, name, normalized_name, category, default_price, source, active_unique_key)
                    VALUES (?, ?, ?, ?, ?, ?, 'DEFAULT', ?)
                    """, foodId, userId, template.name(), template.normalizedName(), template.category(),
                    template.defaultPrice(), template.normalizedName() + "|" + template.category());
            for (String tag : readTags(template.tagsJson())) {
                jdbc.update("INSERT INTO food_option_tags (id, food_option_id, tag, normalized_tag) VALUES (?, ?, ?, ?)",
                        UUID.randomUUID().toString(), foodId, tag, tag.trim().toLowerCase(Locale.ROOT));
            }
        }
    }

    private List<String> readTags(String json) {
        String content = json == null ? "" : json.trim();
        if (content.length() < 2 || !content.startsWith("[") || !content.endsWith("]")) {
            throw new IllegalStateException("默认食物标签数据无效");
        }
        content = content.substring(1, content.length() - 1).trim();
        if (content.isEmpty()) return List.of();
        return Arrays.stream(content.split(","))
                .map(String::trim)
                .map(value -> value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                        ? value.substring(1, value.length() - 1) : value)
                .toList();
    }

    private UserRow findUserByEmail(String email) {
        List<UserRow> users = jdbc.query("""
                SELECT id, email, nickname, avatar_object_key, role, status, onboarding_completed, must_change_password
                FROM users WHERE email = ?
                """, (rs, rowNum) -> new UserRow(rs.getString("id"), rs.getString("email"), rs.getString("nickname"),
                rs.getString("avatar_object_key"), rs.getString("role"), rs.getString("status"),
                rs.getBoolean("onboarding_completed"), rs.getBoolean("must_change_password")), email);
        return users.isEmpty() ? null : users.getFirst();
    }

    private boolean passwordMatches(String password, String userId) {
        List<String> hashes = jdbc.query("SELECT password_hash FROM users WHERE id = ?", (rs, rowNum) -> rs.getString(1), userId);
        return !hashes.isEmpty() && passwordEncoder.matches(password, hashes.getFirst());
    }

    private RefreshTokenRow findRefreshToken(String rawToken) {
        List<RefreshTokenRow> tokens = jdbc.query("""
                SELECT r.id, r.user_id, r.expires_at, r.revoked_at, u.role, u.status
                FROM refresh_tokens r JOIN users u ON u.id = r.user_id
                WHERE r.token_hash = ?
                """, (rs, rowNum) -> new RefreshTokenRow(rs.getString("id"), rs.getString("user_id"),
                rs.getTimestamp("expires_at").toInstant(), rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant(),
                rs.getString("role"), rs.getString("status")), hash(rawToken));
        return tokens.isEmpty() ? null : tokens.getFirst();
    }

    private String nextStep(UserRow user) {
        if (user.mustChangePassword()) return "CHANGE_PASSWORD";
        if ("ADMIN".equals(user.role())) return "ADMIN_HOME";
        return user.onboardingCompleted() ? "HOME" : "ONBOARDING";
    }

    private String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    private String defaultNickname(String email) {
        return "干饭用户" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算令牌摘要", exception);
        }
    }

    private ApiException unauthorized() { return new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "邮箱或密码错误"); }

    private record UserRow(String id, String email, String nickname, String avatarObjectKey, String role, String status,
                           boolean onboardingCompleted, boolean mustChangePassword) { }
    private record TemplateRow(String name, String normalizedName, String category, java.math.BigDecimal defaultPrice, String tagsJson) { }
    private record RefreshTokenRow(String id, String userId, Instant expiresAt, Instant revokedAt, String role, String status) { }
    private record RefreshTokenPair(String rawToken) { }
}

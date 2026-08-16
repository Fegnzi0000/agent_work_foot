package com.hyf.agent_work_foot.auth;

import com.hyf.agent_work_foot.auth.mapper.AuthMapper;
import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.common.AppConstants;
import com.hyf.agent_work_foot.common.AppPermissions;
import com.hyf.agent_work_foot.config.AuthProperties;
import com.hyf.agent_work_foot.food.FoodInitializationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证用例服务。
 *
 * <p>编排注册、登录、Refresh Token 轮换和退出；所有持久化通过 AuthMapper 完成，不直接处理 HTTP 请求或 SQL。</p>
 */
@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthProperties properties;
    private final FoodInitializationService foodInitializationService;
    private final Clock clock;
    private final RolePermissionResolver permissionResolver;
    private final TemporaryCredentialService temporaryCredentialService;

    /**
     * 作用：注入认证流程依赖。
     *
     * <p>输入：数据访问、密码编码、JWT 与认证配置。输出：认证服务实例。
     * 逻辑：保存依赖，具体业务在公开用例方法中执行。</p>
     */
    public AuthService(
            AuthMapper mapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthProperties properties,
            FoodInitializationService foodInitializationService,
            Clock clock,
            RolePermissionResolver permissionResolver,
            TemporaryCredentialService temporaryCredentialService
    ) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
        this.foodInitializationService = foodInitializationService;
        this.clock = clock;
        this.permissionResolver = permissionResolver;
        this.temporaryCredentialService = temporaryCredentialService;
    }

    /**
     * 作用：注册用户并完成首次初始化。
     *
     * <p>输入：校验过格式的注册请求。输出：用户资料、Access Token、Refresh Token 与 ONBOARDING 指引。
     * 逻辑：同一事务内校验邮箱、创建用户、复制默认食物并保存首个 Refresh Token，任一步失败都回滚。</p>
     */
    @Transactional
    public AuthResponses.AuthData register(AuthRequests.RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_CONFIRMATION_MISMATCH", "两次密码输入不一致");
        }
        String email = normalizeEmail(request.email());
        if (mapper.countByEmail(email) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "邮箱已注册");
        }

        AuthMapper.UserRow user = new AuthMapper.UserRow(
                UUID.randomUUID().toString(),
                email,
                "干饭用户" + UUID.randomUUID().toString().substring(0, 8),
                null,
                AppConstants.ROLE_USER,
                AppConstants.USER_STATUS_ACTIVE,
                false,
                false,
                0
        );
        mapper.insertUser(user, passwordEncoder.encode(request.password()));
        foodInitializationService.initializeDefaults(user.id());
        return authData(user, issueRefreshToken(user.id(), null));
    }

    /**
     * 作用：校验账号密码并创建新的登录会话。
     *
     * <p>输入：校验过格式的登录请求。输出：认证数据；账号不存在、禁用或密码不匹配时返回统一失败。
     * 逻辑：按标准化邮箱读取用户，校验启用状态和 BCrypt 哈希，更新登录时间并签发 Token 对。</p>
     */
    @Transactional
    public AuthResponses.AuthData login(AuthRequests.LoginRequest request) {
        AuthMapper.UserWithPassword found = mapper.selectUserByEmail(normalizeEmail(request.email()));
        if (found == null
                || !AppConstants.USER_STATUS_ACTIVE.equals(found.status())
                || !temporaryCredentialService.authenticate(
                        found.id(), found.passwordHash(), found.mustChangePassword(), request.password()
                )) {
            throw unauthorized();
        }
        mapper.updateLastLogin(found.id(), utcNow());
        AuthMapper.UserRow user = new AuthMapper.UserRow(
                found.id(),
                found.email(),
                found.nickname(),
                found.avatarObjectKey(),
                found.role(),
                found.status(),
                found.onboardingCompleted(),
                found.mustChangePassword(),
                found.authVersion()
        );
        return authData(user, issueRefreshToken(user.id(), null));
    }

    /**
     * 作用：轮换一个有效 Refresh Token。
     *
     * <p>输入：客户端保存的 Refresh Token 原文。输出：新的 Access Token 与 Refresh Token。
     * 逻辑：同一事务内校验摘要、撤销旧 Token、保存新 Token；旧 Token 不能再次使用。</p>
     */
    @Transactional
    public AuthResponses.TokenData refresh(String rawToken) {
        AuthMapper.RefreshTokenRow token = mapper.selectRefreshToken(hash(rawToken));
        if (token == null
                || token.revokedAt() != null
                || !token.expiresAt().isAfter(clock.instant())
                || !AppConstants.USER_STATUS_ACTIVE.equals(token.status())) {
            throw tokenInvalid();
        }
        if (mapper.revokeById(token.id(), AppConstants.TOKEN_REVOKE_ROTATED, utcNow()) != 1) {
            throw tokenInvalid();
        }
        RefreshTokenPair pair = issueRefreshToken(token.userId(), token.id());
        return new AuthResponses.TokenData(
                jwtService.issueAccessToken(token.userId(), token.role(), token.authVersion()),
                properties.jwt().accessTokenTtl().toSeconds(),
                pair.rawToken(),
                properties.jwt().refreshTokenTtl().toSeconds()
        );
    }

    /**
     * 作用：退出当前设备会话。
     *
     * <p>输入：当前设备的 Refresh Token 原文。输出：无；Token 不存在或已撤销时返回统一认证失败。
     * 逻辑：仅按摘要撤销一条未撤销 Token，不撤销该用户其他会话。</p>
     */
    public void logout(String rawToken) {
        if (mapper.revokeByHash(hash(rawToken), AppConstants.TOKEN_REVOKE_LOGOUT, utcNow()) == 0) {
            throw tokenInvalid();
        }
    }

    /**
     * 作用：组装注册或登录响应。
     *
     * <p>输入：已持久化的用户资料和新 Refresh Token。输出：完整认证响应。
     * 逻辑：签发 Access Token、转换公开用户资料，并根据角色和引导状态确定下一步。</p>
     */
    private AuthResponses.AuthData authData(AuthMapper.UserRow user, RefreshTokenPair refreshToken) {
        return new AuthResponses.AuthData(
                jwtService.issueAccessToken(user.id(), user.role(), user.authVersion()),
                properties.jwt().accessTokenTtl().toSeconds(),
                refreshToken.rawToken(),
                properties.jwt().refreshTokenTtl().toSeconds(),
                new AuthResponses.UserData(
                        user.id(), user.email(), user.nickname(), user.avatarObjectKey(), user.role(),
                        user.status(), user.onboardingCompleted(), user.mustChangePassword()
                ),
                nextStep(user)
        );
    }

    /**
     * 作用：生成、摘要并保存新的 Refresh Token。
     *
     * <p>输入：归属用户 ID 和可为空的父 Token ID。输出：仅含原文的临时 Token 对象。
     * 逻辑：随机生成 48 字节原文，仅将 SHA-256 摘要持久化，原文只在本次响应中返回。</p>
     */
    private RefreshTokenPair issueRefreshToken(String userId, String parentId) {
        byte[] value = new byte[48];
        RANDOM.nextBytes(value);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        mapper.insertRefreshToken(new AuthMapper.RefreshTokenInsert(
                UUID.randomUUID().toString(),
                userId,
                hash(raw),
                parentId,
                clock.instant().plus(properties.jwt().refreshTokenTtl())
        ));
        return new RefreshTokenPair(raw);
    }

    /** 作用：确定客户端完成认证后的下一页面。输入：用户资料。输出：页面状态字符串。逻辑：优先强制改密，再管理员页，最后按引导状态判断。 */
    private String nextStep(AuthMapper.UserRow user) {
        if (user.mustChangePassword()) {
            return "CHANGE_PASSWORD";
        }
        if (permissionResolver.hasPermission(user.id(), AppPermissions.ADMIN_PORTAL_ACCESS)) {
            return "ADMIN_HOME";
        }
        return user.onboardingCompleted() ? "HOME" : "ONBOARDING";
    }

    /** 作用：规范化邮箱。输入：原始邮箱。输出：去首尾空格并转小写的邮箱。逻辑：统一注册、登录和限流键的比较口径。 */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** 作用：计算敏感 Token 的 SHA-256 摘要。输入：原文。输出：十六进制摘要。逻辑：数据库只保存摘要，算法不可用时中止流程。 */
    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算令牌摘要", exception);
        }
    }

    /** 作用：创建统一登录凭据失败异常。输入：无。输出：AUTH_INVALID_CREDENTIALS 异常。逻辑：不暴露账号、密码或 Token 的具体失败原因。 */
    private ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "邮箱或密码错误");
    }

    /** 作用：创建统一Token失败异常。输入：无。输出：AUTH_TOKEN_INVALID异常。逻辑：刷新和退出不复用登录凭据错误。 */
    private ApiException tokenInvalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID", "Token无效或已过期");
    }

    /** 作用：取得UTC数据库时间。输入：无。输出：UTC LocalDateTime。逻辑：认证时间统一来自可注入Clock。 */
    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /** 仅在服务内部传递新生成的 Refresh Token 原文，避免将原文写入持久化对象。 */
    private record RefreshTokenPair(String rawToken) {
    }
}

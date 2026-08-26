package com.hyf.agent_work_foot.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hyf.agent_work_foot.admin.entity.TemporaryPasswordEntity;
import com.hyf.agent_work_foot.admin.mapper.AdminUserMapper;
import com.hyf.agent_work_foot.admin.mapper.TemporaryPasswordMapper;
import com.hyf.agent_work_foot.auth.mapper.AccountSecurityMapper;
import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.common.AppConstants;
import com.hyf.agent_work_foot.config.AdminProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin应用服务，编排用户查询、状态转换、临时密码、会话失效和审计事务。 */
@Service
public class AdminService {
    private final AdminUserMapper userMapper;
    private final TemporaryPasswordMapper temporaryPasswordMapper;
    private final AccountSecurityMapper accountSecurityMapper;
    private final AdminAuditService auditService;
    private final AdminRateLimiter rateLimiter;
    private final TemporaryPasswordGenerator passwordGenerator;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties properties;
    private final Clock clock;

    /** 作用：注入管理员用例依赖。输入：数据访问、安全、限流、随机密码、配置和时钟。输出：服务实例。逻辑：所有高风险写入由本服务定义事务边界。 */
    public AdminService(
            AdminUserMapper userMapper,
            TemporaryPasswordMapper temporaryPasswordMapper,
            AccountSecurityMapper accountSecurityMapper,
            AdminAuditService auditService,
            AdminRateLimiter rateLimiter,
            TemporaryPasswordGenerator passwordGenerator,
            PasswordEncoder passwordEncoder,
            AdminProperties properties,
            Clock clock
    ) {
        this.userMapper = userMapper;
        this.temporaryPasswordMapper = temporaryPasswordMapper;
        this.accountSecurityMapper = accountSecurityMapper;
        this.auditService = auditService;
        this.rateLimiter = rateLimiter;
        this.passwordGenerator = passwordGenerator;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    /** 作用：分页查询普通用户。输入：管理员、邮箱前缀、状态和0基分页。输出：最小账号分页。逻辑：管理员列表只看USER。 */
    public AdminResponses.AdminUserPageData list(
            String adminUserId,
            String email,
            String status,
            int page,
            int size
    ) {
        validatePage(page, size);
        String normalizedStatus = normalizeStatus(status);
        String emailPrefix = normalizeEmailPrefix(email);
        IPage<AdminUserMapper.AdminUserRow> result = userMapper.selectAdminUserPage(
                new Page<>(page + 1L, size), emailPrefix, normalizedStatus
        );
        List<AdminResponses.AdminUserData> items = result.getRecords().stream().map(this::response).toList();
        return new AdminResponses.AdminUserPageData(
                items, page, size, result.getTotal(), result.getPages()
        );
    }

    /** 作用：启用或禁用目标账号。输入：管理员、目标和期望状态。输出：更新后公开资料。逻辑：行锁、权限、状态机、会话撤销与成功审计同事务。 */
    @Transactional
    public AdminResponses.AdminUserData updateStatus(String adminUserId, String targetUserId, String requestedStatus) {
        String status = normalizeRequiredStatus(requestedStatus);
        AdminUserMapper.LockedAdminUser target = requiredLocked(adminUserId, targetUserId, "USER_STATUS_UPDATE");
        validateTargetAuthority(adminUserId, target, "USER_STATUS_UPDATE");
        if (AppConstants.USER_STATUS_CANCELLED.equals(target.status())) {
            reject(adminUserId, target.id(), "USER_STATUS_UPDATE", HttpStatus.CONFLICT,
                    "USER_STATUS_TRANSITION_INVALID", "注销账号不能恢复");
        }
        if (status.equals(target.status())) {
            auditService.success(adminUserId, target.id(), "USER_STATUS_UNCHANGED",
                    Map.of("before", target.status(), "after", status, "targetRole", target.role()));
            return response(userMapper.selectById(target.id()));
        }
        if (userMapper.updateStatus(target.id(), status, target.authVersion()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_STATUS_TRANSITION_INVALID", "账号状态已发生变化");
        }
        if (AppConstants.USER_STATUS_DISABLED.equals(status)) {
            LocalDateTime now = utcNow();
            accountSecurityMapper.revokeAllRefreshTokens(
                    target.id(), now, AppConstants.TOKEN_REVOKE_ACCOUNT_DISABLED
            );
            accountSecurityMapper.revokeTemporaryPasswords(target.id(), now);
        }
        auditService.success(adminUserId, target.id(),
                AppConstants.USER_STATUS_DISABLED.equals(status) ? "USER_DISABLED" : "USER_ENABLED",
                Map.of("before", target.status(), "after", status, "targetRole", target.role()));
        return response(userMapper.selectById(target.id()));
    }

    /** 作用：生成并保存一次性临时密码。输入：管理员、目标和来源IP。输出：仅本次可见明文与过期时间。逻辑：限流后在同一事务替换密码、撤销会话并审计。 */
    @Transactional
    public AdminResponses.TemporaryPasswordData createTemporaryPassword(
            String adminUserId,
            String targetUserId,
            String ipAddress
    ) {
        rateLimiter.checkTemporaryPassword(adminUserId, targetUserId, ipAddress);
        AdminUserMapper.LockedAdminUser target = requiredLocked(
                adminUserId, targetUserId, "TEMP_PASSWORD_CREATE"
        );
        validateTargetAuthority(adminUserId, target, "TEMP_PASSWORD_CREATE");
        if (!AppConstants.USER_STATUS_ACTIVE.equals(target.status())) {
            reject(adminUserId, target.id(), "TEMP_PASSWORD_CREATE", HttpStatus.CONFLICT,
                    "USER_ACCOUNT_NOT_ACTIVE", "只能为启用账号生成临时密码");
        }

        String rawPassword = passwordGenerator.generate();
        String passwordHash = passwordEncoder.encode(rawPassword);
        LocalDateTime now = utcNow();
        LocalDateTime expiresAt = now.plus(properties.temporaryPasswordTtl());
        accountSecurityMapper.revokeTemporaryPasswords(target.id(), now);
        if (userMapper.resetPassword(target.id(), passwordHash, target.authVersion()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_ACCOUNT_NOT_ACTIVE", "账号状态已发生变化");
        }
        accountSecurityMapper.revokeAllRefreshTokens(
                target.id(), now, AppConstants.TOKEN_REVOKE_ADMIN_PASSWORD_RESET
        );
        TemporaryPasswordEntity entity = new TemporaryPasswordEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setUserId(target.id());
        entity.setPasswordHash(passwordHash);
        entity.setCreatedByAdminId(adminUserId);
        entity.setExpiresAt(expiresAt);
        entity.setCreatedAt(now);
        temporaryPasswordMapper.insert(entity);
        auditService.success(adminUserId, target.id(), "TEMP_PASSWORD_CREATED",
                Map.of("expiresAt", expiresAt.toString(), "targetRole", target.role()));
        return new AdminResponses.TemporaryPasswordData(
                rawPassword, expiresAt.toInstant(ZoneOffset.UTC)
        );
    }

    /** 作用：读取并锁定目标，不存在时写失败审计。输入：管理员、目标和动作。输出：锁定账号。逻辑：资源不存在统一404。 */
    private AdminUserMapper.LockedAdminUser requiredLocked(
            String adminUserId,
            String targetUserId,
            String action
    ) {
        AdminUserMapper.LockedAdminUser target = userMapper.selectForUpdate(targetUserId);
        if (target == null) {
            reject(adminUserId, targetUserId, action, HttpStatus.NOT_FOUND,
                    "RESOURCE_NOT_FOUND", "用户不存在");
        }
        return target;
    }

    /** 作用：校验管理目标。输入：操作者、目标和动作。输出：允许时无返回。逻辑：管理员只可操作USER。 */
    private void validateTargetAuthority(
            String adminUserId,
            AdminUserMapper.LockedAdminUser target,
            String action
    ) {
        if (!AppConstants.ROLE_USER.equals(target.role())) {
            reject(adminUserId, target.id(), action, HttpStatus.FORBIDDEN,
                    "ADMIN_TARGET_FORBIDDEN", "无权操作该管理员账号");
        }
    }

    /** 作用：记录业务拒绝并抛出统一异常。输入：审计和API错误字段。输出：不返回。逻辑：失败审计独立事务提交。 */
    private void reject(
            String adminUserId,
            String targetUserId,
            String action,
            HttpStatus status,
            String code,
            String message
    ) {
        auditService.failure(adminUserId, targetUserId, action, code);
        throw new ApiException(status, code, message);
    }

    /** 作用：转换数据库行到HTTP响应。输入：公开用户投影。输出：AdminUserData。逻辑：不附加任何敏感字段。 */
    private AdminResponses.AdminUserData response(AdminUserMapper.AdminUserRow row) {
        return new AdminResponses.AdminUserData(
                row.id(), row.email(), row.nickname(), row.role(), row.status(),
                row.onboardingCompleted(), row.mustChangePassword(), row.createdAt(), row.lastLoginAt()
        );
    }

    /** 作用：校验0基分页。输入：page和size。输出：无。逻辑：沿用全项目0基、最大100约束。 */
    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "分页参数不合法");
        }
    }

    /** 作用：标准化可选状态筛选。输入：可空字符串。输出：大写状态或null。逻辑：只接受三种数据库账号状态。 */
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of(AppConstants.USER_STATUS_ACTIVE, AppConstants.USER_STATUS_DISABLED,
                AppConstants.USER_STATUS_CANCELLED).contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "账号状态筛选不合法");
        }
        return normalized;
    }

    /** 作用：标准化状态修改值。输入：请求状态。输出：ACTIVE或DISABLED。逻辑：防御绕过Bean Validation的内部调用。 */
    private String normalizeRequiredStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!List.of(AppConstants.USER_STATUS_ACTIVE, AppConstants.USER_STATUS_DISABLED).contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "账号状态不合法");
        }
        return normalized;
    }

    /** 作用：规范化并转义邮箱前缀。输入：可空搜索文本。输出：小写LIKE前缀或null。逻辑：限制长度并把LIKE元字符视为普通字符。 */
    private String normalizeEmailPrefix(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "邮箱搜索内容过长");
        }
        return normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** 作用：获取UTC数据库时间。输入：无。输出：LocalDateTime。逻辑：所有安全时间来自可注入Clock。 */
    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}

package com.hyf.agent_work_foot.auth;

import com.hyf.agent_work_foot.auth.mapper.AccountSecurityMapper;
import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.common.AppConstants;
import com.hyf.agent_work_foot.common.FieldErrorDetail;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 认证模块账号安全服务，统一处理数据库访问状态、正式改密、软注销和全部会话失效。 */
@Service
public class AccountSecurityService {
    private final AccountSecurityMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    /** 作用：注入账号安全依赖。输入：安全Mapper、密码编码器和UTC时钟。输出：服务实例。逻辑：不处理HTTP对象。 */
    public AccountSecurityService(AccountSecurityMapper mapper, PasswordEncoder passwordEncoder, Clock clock) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** 作用：读取JWT认证所需当前账号状态。输入：JWT用户ID。输出：状态或空。逻辑：供认证Filter逐请求核对版本和角色。 */
    public AccountSecurityMapper.AccessState accessState(String userId) {
        return mapper.selectAccessState(userId);
    }

    /**
     * 作用：修改当前用户正式密码并下线全部旧会话。
     * 输入：JWT用户ID、当前密码、新密码和确认密码。输出：无。
     * 逻辑：锁行后验证状态与密码，原子更新哈希、安全版本、强制改密标记并撤销全部Refresh Token和临时密码。
     */
    @Transactional
    public void changePassword(String userId, String currentPassword, String newPassword,
                               String confirmNewPassword) {
        if (!newPassword.equals(confirmNewPassword)) {
            throw validation("PASSWORD_CONFIRMATION_MISMATCH", "两次新密码输入不一致",
                    "confirmNewPassword", "必须与newPassword一致");
        }
        AccountSecurityMapper.LockedAccount account = activeLocked(userId);
        if (!passwordEncoder.matches(currentPassword, account.passwordHash())) {
            throw currentPasswordIncorrect();
        }
        if (passwordEncoder.matches(newPassword, account.passwordHash())) {
            throw validation("PASSWORD_UNCHANGED", "新密码不能与当前密码相同",
                    "newPassword", "不能与当前密码相同");
        }
        if (mapper.updatePassword(userId, passwordEncoder.encode(newPassword), account.authVersion()) != 1) {
            throw tokenInvalid();
        }
        LocalDateTime now = utcNow();
        mapper.revokeAllRefreshTokens(userId, now, AppConstants.TOKEN_REVOKE_PASSWORD_CHANGED);
        mapper.revokeTemporaryPasswords(userId, now);
    }

    /**
     * 作用：软注销当前普通用户并下线全部会话。
     * 输入：JWT用户ID、当前密码和严格确认文字。输出：无。
     * 逻辑：锁行验证后写入CANCELLED、UTC时间和新安全版本，并撤销Refresh Token与临时密码。
     */
    @Transactional
    public void cancelAccount(String userId, String currentPassword, String confirmation) {
        if (!"CANCEL".equals(confirmation)) {
            throw validation("VALIDATION_FAILED", "请求参数不合法", "confirmation", "必须严格等于CANCEL");
        }
        AccountSecurityMapper.LockedAccount account = activeLocked(userId);
        if (account.mustChangePassword()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED", "请先修改密码");
        }
        if (!passwordEncoder.matches(currentPassword, account.passwordHash())) {
            throw currentPasswordIncorrect();
        }
        LocalDateTime now = utcNow();
        if (mapper.cancelAccount(userId, account.authVersion(), now) != 1) {
            throw tokenInvalid();
        }
        mapper.revokeAllRefreshTokens(userId, now, AppConstants.TOKEN_REVOKE_ACCOUNT_CANCELLED);
        mapper.revokeTemporaryPasswords(userId, now);
    }

    /** 作用：锁定并确认ACTIVE账号。输入：用户ID。输出：锁定账号。逻辑：不存在、禁用或注销统一视为Token无效。 */
    private AccountSecurityMapper.LockedAccount activeLocked(String userId) {
        AccountSecurityMapper.LockedAccount account = mapper.selectForUpdate(userId);
        if (account == null || !AppConstants.USER_STATUS_ACTIVE.equals(account.status())) {
            throw tokenInvalid();
        }
        return account;
    }

    /** 作用：生成当前密码错误。输入：无。输出：400业务异常。逻辑：不记录或回显密码原文。 */
    private ApiException currentPasswordIncorrect() {
        return validation("CURRENT_PASSWORD_INCORRECT", "当前密码错误",
                "currentPassword", "与当前账号密码不匹配");
    }

    /** 作用：生成字段业务错误。输入：错误码、消息、字段和原因。输出：400异常。逻辑：保持前端字段定位。 */
    private ApiException validation(String code, String message, String field, String reason) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message,
                List.of(new FieldErrorDetail(field, reason)));
    }

    /** 作用：生成统一Token无效错误。输入：无。输出：401异常。逻辑：隐藏账号禁用、注销或并发状态细节。 */
    private ApiException tokenInvalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID", "登录状态无效");
    }

    /** 作用：取得UTC数据库时间。输入：无。输出：UTC LocalDateTime。逻辑：统一使用可注入Clock便于测试。 */
    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}

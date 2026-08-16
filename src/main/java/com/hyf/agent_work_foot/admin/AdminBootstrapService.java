package com.hyf.agent_work_foot.admin;

import com.hyf.agent_work_foot.admin.mapper.AdminUserMapper;
import com.hyf.agent_work_foot.auth.mapper.AccountSecurityMapper;
import com.hyf.agent_work_foot.common.AppConstants;
import com.hyf.agent_work_foot.rbac.mapper.RbacMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 受控管理员初始化服务，只提升已注册ACTIVE普通用户，不创建账号或接触明文密码。 */
@Service
public class AdminBootstrapService {
    private final AdminUserMapper userMapper;
    private final RbacMapper rbacMapper;
    private final AccountSecurityMapper accountSecurityMapper;
    private final Clock clock;

    /** 作用：注入初始化事务依赖。输入：用户、RBAC、会话Mapper和时钟。输出：服务实例。逻辑：该服务不暴露Controller。 */
    public AdminBootstrapService(
            AdminUserMapper userMapper,
            RbacMapper rbacMapper,
            AccountSecurityMapper accountSecurityMapper,
            Clock clock
    ) {
        this.userMapper = userMapper;
        this.rbacMapper = rbacMapper;
        this.accountSecurityMapper = accountSecurityMapper;
        this.clock = clock;
    }

    /** 作用：把一个已注册普通用户提升为管理员。输入：标准邮箱候选和ADMIN/SUPER_ADMIN角色。输出：目标用户ID。逻辑：行锁、角色更新、安全版本和会话撤销同事务，重复同角色幂等。 */
    @Transactional
    public String promote(String email, String roleCode) {
        String normalizedEmail = requireEmail(email);
        String normalizedRole = roleCode == null ? "" : roleCode.trim().toUpperCase(Locale.ROOT);
        if (!List.of(AppConstants.ROLE_ADMIN, AppConstants.ROLE_SUPER_ADMIN).contains(normalizedRole)) {
            throw new IllegalArgumentException("bootstrap-admin角色只能是ADMIN或SUPER_ADMIN");
        }
        AdminUserMapper.LockedAdminUser user = userMapper.selectByEmailForUpdate(normalizedEmail);
        if (user == null || !AppConstants.USER_STATUS_ACTIVE.equals(user.status())) {
            throw new IllegalStateException("bootstrap-admin目标必须是已注册ACTIVE用户");
        }
        if (normalizedRole.equals(user.role())) {
            return user.id();
        }
        if (!AppConstants.ROLE_USER.equals(user.role())) {
            throw new IllegalStateException("bootstrap-admin只允许提升普通USER");
        }
        RbacMapper.RoleRow role = rbacMapper.selectActiveRoleByCode(normalizedRole);
        if (role == null || userMapper.updateRole(user.id(), role.id(), user.authVersion()) != 1) {
            throw new IllegalStateException("bootstrap-admin角色提升失败");
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        accountSecurityMapper.revokeAllRefreshTokens(user.id(), now, AppConstants.TOKEN_REVOKE_ROLE_CHANGED);
        accountSecurityMapper.revokeTemporaryPasswords(user.id(), now);
        return user.id();
    }

    /** 作用：校验并标准化初始化邮箱。输入：配置邮箱。输出：小写邮箱。逻辑：空值直接拒绝，避免误操作。 */
    private String requireEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("bootstrap-admin必须提供目标邮箱");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

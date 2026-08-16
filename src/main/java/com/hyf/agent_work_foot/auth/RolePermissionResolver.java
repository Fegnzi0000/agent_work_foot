package com.hyf.agent_work_foot.auth;

import com.hyf.agent_work_foot.common.AppPermissions;
import com.hyf.agent_work_foot.rbac.mapper.RbacMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * 数据库角色到接口权限的集中解析器。
 *
 * <p>权限来源为roles、permissions与role_permissions；强制改密状态会覆盖数据库权限，只保留最小账号能力。</p>
 */
@Component
public class RolePermissionResolver {
    private final RbacMapper mapper;

    /** 作用：注入RBAC Mapper。输入：权限查询接口。输出：解析器实例。逻辑：每次请求读取数据库当前权限。 */
    public RolePermissionResolver(RbacMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 作用：将JWT角色转换为Security authority。
     * 输入：数据库当前角色和强制改密状态。输出：角色authority与接口权限列表。
     * 逻辑：强制改密时仅授予身份读取和改密能力；正常状态完全加载数据库当前角色权限。
     */
    public List<GrantedAuthority> resolve(String userId, String role, boolean mustChangePassword) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        if (mustChangePassword) {
            authorities.add(new SimpleGrantedAuthority(AppPermissions.ACCOUNT_SELF_VIEW));
            authorities.add(new SimpleGrantedAuthority(AppPermissions.ACCOUNT_CHANGE_PASSWORD));
            authorities.add(new SimpleGrantedAuthority(AppPermissions.PASSWORD_CHANGE_REQUIRED_STATE));
            return List.copyOf(authorities);
        }
        mapper.selectPermissionCodesByUserId(userId).stream()
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        return List.copyOf(authorities);
    }

    /** 作用：判断用户当前角色是否拥有指定权限。输入：用户ID和权限编码。输出：布尔值。逻辑：用于登录导航和跨模块目标校验。 */
    public boolean hasPermission(String userId, String permission) {
        return mapper.selectPermissionCodesByUserId(userId).contains(permission);
    }
}

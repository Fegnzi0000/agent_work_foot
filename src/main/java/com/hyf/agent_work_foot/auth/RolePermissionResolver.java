package com.hyf.agent_work_foot.auth;

import com.hyf.agent_work_foot.common.AppConstants;
import com.hyf.agent_work_foot.common.AppPermissions;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * 当前静态角色到接口权限的集中解析器。
 *
 * <p>USER拥有food五项权限，ADMIN暂不拥有；未来动态RBAC只需替换本组件的数据来源。</p>
 */
@Component
public class RolePermissionResolver {
    /**
     * 作用：将JWT角色转换为Security authority。
     * 输入：已验证角色。输出：角色authority与接口权限列表。
     * 逻辑：始终保留ROLE_*，仅USER追加food权限。
     */
    public List<GrantedAuthority> resolve(String role) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        if (AppConstants.ROLE_USER.equals(role)) {
            authorities.add(new SimpleGrantedAuthority(AppPermissions.FOOD_LIST));
            authorities.add(new SimpleGrantedAuthority(AppPermissions.FOOD_VIEW));
            authorities.add(new SimpleGrantedAuthority(AppPermissions.FOOD_CREATE));
            authorities.add(new SimpleGrantedAuthority(AppPermissions.FOOD_UPDATE));
            authorities.add(new SimpleGrantedAuthority(AppPermissions.FOOD_DELETE));
        }
        return List.copyOf(authorities);
    }
}

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
     * 输入：数据库当前角色和强制改密状态。输出：角色authority与接口权限列表。
     * 逻辑：强制改密时仅授予身份读取和改密能力；正常USER追加全部普通业务权限。
     */
    public List<GrantedAuthority> resolve(String role, boolean mustChangePassword) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        authorities.add(new SimpleGrantedAuthority(AppPermissions.ACCOUNT_SELF_VIEW));
        authorities.add(new SimpleGrantedAuthority(AppPermissions.ACCOUNT_CHANGE_PASSWORD));
        if (mustChangePassword) {
            authorities.add(new SimpleGrantedAuthority(AppPermissions.PASSWORD_CHANGE_REQUIRED_STATE));
            return List.copyOf(authorities);
        }
        if (AppConstants.ROLE_USER.equals(role)) {
            authorities.add(new SimpleGrantedAuthority(AppPermissions.ACCOUNT_CANCEL));
            authorities.add(new SimpleGrantedAuthority(AppPermissions.FOOD_LIST));
            authorities.add(new SimpleGrantedAuthority(AppPermissions.FOOD_VIEW));
            authorities.add(new SimpleGrantedAuthority(AppPermissions.FOOD_CREATE));
            authorities.add(new SimpleGrantedAuthority(AppPermissions.FOOD_UPDATE));
            authorities.add(new SimpleGrantedAuthority(AppPermissions.FOOD_DELETE));
            authorities.add(new SimpleGrantedAuthority(AppPermissions.DIET_LIST));
            authorities.add(new SimpleGrantedAuthority(AppPermissions.DIET_CREATE));
            authorities.add(new SimpleGrantedAuthority(AppPermissions.DIET_UPDATE));
            authorities.add(new SimpleGrantedAuthority(AppPermissions.DIET_DELETE));
            authorities.add(new SimpleGrantedAuthority(AppPermissions.DIET_STATISTICS));
            authorities.add(new SimpleGrantedAuthority(AppPermissions.SLOT_SPIN));
            authorities.add(new SimpleGrantedAuthority(AppPermissions.SLOT_CONFIRM));
        }
        return List.copyOf(authorities);
    }
}

package com.hyf.agent_work_foot.rbac.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 动态RBAC数据访问接口，负责角色、权限映射与系统角色查询，不承载HTTP或业务事务。 */
public interface RbacMapper {
    /** 作用：读取用户当前角色的全部有效权限。输入：用户ID。输出：权限编码列表。逻辑：只接受启用角色并按编码稳定排序。 */
    List<String> selectPermissionCodesByUserId(@Param("userId") String userId);

    /** 作用：按编码读取有效角色。输入：角色编码。输出：角色或空。逻辑：供注册和受控管理员初始化解析角色主键。 */
    RoleRow selectActiveRoleByCode(@Param("code") String code);

    /** 作用：统计有效超级管理员。输入：无。输出：ACTIVE SUPER_ADMIN数量。逻辑：防止禁用最后一个超级管理员。 */
    long countActiveSuperAdmins();

    /** RBAC角色轻量投影。 */
    record RoleRow(String id, String code, String name) {
    }
}

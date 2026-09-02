package com.hyf.agent_work_foot.admin.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.Instant;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

/** Admin用户数据访问接口，负责普通用户分页、账号行锁、状态、密码和角色更新。 */
public interface AdminUserMapper {
    /** 作用：分页查询普通用户。输入：MP分页、邮箱、昵称、状态和注册时间范围。输出：分页投影。逻辑：管理员列表永远只返回USER。 */
    IPage<AdminUserRow> selectAdminUserPage(
            Page<AdminUserRow> page,
            @Param("emailPrefix") String emailPrefix,
            @Param("nicknameContains") String nicknameContains,
            @Param("status") String status,
            @Param("registeredStartAt") LocalDateTime registeredStartAt,
            @Param("registeredEndExclusiveAt") LocalDateTime registeredEndExclusiveAt
    );

    /** 作用：按ID锁定管理目标。输入：用户ID。输出：账号状态或空。逻辑：状态修改和密码重置串行执行。 */
    LockedAdminUser selectForUpdate(@Param("userId") String userId);

    /** 作用：读取更新后的公开账号资料。输入：用户ID。输出：列表同形投影或空。逻辑：用于状态接口回读最终数据库值。 */
    AdminUserRow selectById(@Param("userId") String userId);

    /** 作用：按邮箱锁定管理员初始化目标。输入：标准化邮箱。输出：账号状态或空。逻辑：bootstrap无HTTP提权入口。 */
    LockedAdminUser selectByEmailForUpdate(@Param("email") String email);

    /** 作用：条件更新账号状态。输入：用户、状态和预期安全版本。输出：影响行数。逻辑：真实转换递增auth_version。 */
    int updateStatus(@Param("userId") String userId, @Param("status") String status,
                     @Param("expectedVersion") int expectedVersion);

    /** 作用：管理员重置密码。输入：目标、临时密码哈希和预期版本。输出：影响行数。逻辑：替换当前密码并进入强制改密。 */
    int resetPassword(@Param("userId") String userId, @Param("passwordHash") String passwordHash,
                      @Param("expectedVersion") int expectedVersion);

    /** 作用：受控提升为管理员并设置登录名。输入：目标、角色、账号和预期版本。输出：影响行数。逻辑：角色和登录名同事务写入。 */
    int promoteToAdmin(@Param("userId") String userId, @Param("roleId") String roleId,
                       @Param("adminLoginName") String adminLoginName, @Param("expectedVersion") int expectedVersion);

    /** 作用：为既有管理员变更登录名。输入：目标、账号和预期版本。输出：影响行数。逻辑：递增安全版本使旧会话失效。 */
    int updateAdminLoginName(@Param("userId") String userId, @Param("adminLoginName") String adminLoginName,
                             @Param("expectedVersion") int expectedVersion);

    /** 管理员分页用户投影。 */
    record AdminUserRow(
            String id,
            String email,
            String nickname,
            String role,
            String status,
            boolean onboardingCompleted,
            boolean mustChangePassword,
            Instant createdAt,
            Instant lastLoginAt
    ) {
    }

    /** 高风险操作锁定的账号状态。 */
    record LockedAdminUser(
            String id,
            String email,
            String adminLoginName,
            String role,
            String status,
            int authVersion,
            boolean mustChangePassword
    ) {
    }
}

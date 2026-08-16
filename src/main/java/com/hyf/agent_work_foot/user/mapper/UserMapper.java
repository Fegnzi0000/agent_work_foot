package com.hyf.agent_work_foot.user.mapper;

import com.hyf.agent_work_foot.auth.mapper.AuthMapper.UserRow;
import org.apache.ibatis.annotations.Param;

/**
 * 用户模块的 MyBatis 数据访问接口。
 *
 * <p>只操作当前认证用户的资料与引导状态；users 实体定义复用认证模块的 UserRow，SQL 位于对应 XML 并使用预编译参数。</p>
 */
public interface UserMapper {
    /** 作用：按 ID 查询当前用户资料。输入：JWT 来源的用户 ID。输出：用户行或 null。逻辑：不接受任意筛选条件。 */
    UserRow selectById(@Param("userId") String userId);

    /** 作用：更新用户昵称。输入：用户 ID 与已清理昵称。输出：无。逻辑：仅更新目标用户的 nickname。 */
    void updateNickname(@Param("userId") String userId, @Param("nickname") String nickname);

    /** 作用：标记用户已完成引导。输入：用户 ID。输出：无。逻辑：仅将 onboarding_completed 设置为真。 */
    void completeOnboarding(@Param("userId") String userId);

    /**
     * 作用：修改指定用户的账号状态。
     * 输入：用户 ID 与受支持的状态常量。输出：影响行数。
     * 逻辑：同时递增安全版本，使禁用或重新启用前签发的Access Token永久失效。
     */
    int updateStatus(@Param("userId") String userId, @Param("status") String status);
}

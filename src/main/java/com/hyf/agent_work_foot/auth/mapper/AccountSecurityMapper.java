package com.hyf.agent_work_foot.auth.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

/** 账号安全数据访问接口，负责访问状态校验、密码行锁、安全版本和全部会话撤销。 */
public interface AccountSecurityMapper {
    /** 作用：读取Access Token校验所需账号状态。输入：JWT用户ID。输出：轻量安全状态或空。逻辑：不返回密码和个人业务数据。 */
    AccessState selectAccessState(@Param("userId") String userId);

    /** 作用：锁定账号安全行。输入：JWT用户ID。输出：密码及状态或空。逻辑：串行化改密和注销。 */
    LockedAccount selectForUpdate(@Param("userId") String userId);

    /** 作用：更新正式密码并递增安全版本。输入：用户、哈希和预期版本。输出：影响行数。逻辑：同时清除强制改密标记。 */
    int updatePassword(@Param("userId") String userId, @Param("passwordHash") String passwordHash,
                       @Param("expectedVersion") int expectedVersion);

    /** 作用：软注销账号并递增安全版本。输入：用户、预期版本和UTC时间。输出：影响行数。逻辑：只允许ACTIVE账号注销。 */
    int cancelAccount(@Param("userId") String userId, @Param("expectedVersion") int expectedVersion,
                      @Param("cancelledAt") LocalDateTime cancelledAt);

    /** 作用：撤销用户全部有效Refresh Token。输入：用户、UTC时间和原因。输出：影响行数。逻辑：已撤销Token保持原原因。 */
    int revokeAllRefreshTokens(@Param("userId") String userId, @Param("revokedAt") LocalDateTime revokedAt,
                               @Param("reason") String reason);

    /** 作用：撤销用户全部未使用临时密码。输入：用户和UTC时间。输出：影响行数。逻辑：为未来Admin临时密码流程预留安全收口。 */
    int revokeTemporaryPasswords(@Param("userId") String userId, @Param("revokedAt") LocalDateTime revokedAt);

    /** 作用：标记账号必须改密并使旧JWT失效。输入：用户和预期版本。输出：影响行数。逻辑：供未来临时密码签发流程复用。 */
    int requirePasswordChange(@Param("userId") String userId,
                              @Param("expectedVersion") int expectedVersion);

    /** JWT认证请求所需的数据库当前状态。 */
    record AccessState(String id, String role, String status, int authVersion, boolean mustChangePassword) {
    }

    /** 改密和注销事务锁定的账号凭据状态。 */
    record LockedAccount(String id, String passwordHash, String status, int authVersion,
                         boolean mustChangePassword) {
    }
}

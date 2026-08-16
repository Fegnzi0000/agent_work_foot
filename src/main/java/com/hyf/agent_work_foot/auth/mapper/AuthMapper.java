package com.hyf.agent_work_foot.auth.mapper;

import java.time.Instant;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

/**
 * 认证模块的 MyBatis 数据访问接口。
 *
 * <p>只负责 users 与 refresh_tokens；食物初始化已经归还food模块。SQL变量必须使用#{...}预编译绑定。</p>
 */
public interface AuthMapper {
    /** 作用：统计指定邮箱的用户数。输入：已标准化邮箱。输出：匹配数量。逻辑：供注册判断邮箱唯一性。 */
    long countByEmail(@Param("email") String email);

    /** 作用：写入新用户和密码哈希。输入：用户公开字段、BCrypt 哈希。输出：无。逻辑：不保存密码明文。 */
    void insertUser(@Param("user") UserRow user, @Param("passwordHash") String passwordHash);

    /** 作用：按邮箱读取登录所需资料。输入：已标准化邮箱。输出：用户与密码哈希，未找到时为 null。逻辑：仅供登录服务校验。 */
    UserWithPassword selectUserByEmail(@Param("email") String email);

    /** 作用：更新成功登录时间。输入：用户 ID。输出：无。逻辑：只更新 last_login_at。 */
    void updateLastLogin(@Param("userId") String userId, @Param("loggedInAt") LocalDateTime loggedInAt);

    /** 作用：按 Token 摘要读取会话及用户状态。输入：SHA-256 摘要。输出：Token 与角色状态，未找到时为 null。逻辑：供刷新流程一次性校验。 */
    RefreshTokenRow selectRefreshToken(@Param("tokenHash") String tokenHash);

    /** 作用：保存新 Refresh Token 摘要。输入：Token 持久化字段。输出：无。逻辑：原文永不传入数据库。 */
    void insertRefreshToken(@Param("token") RefreshTokenInsert token);

    /** 作用：按主键条件撤销 Token。输入：Token ID、原因和时间。输出：影响行数。逻辑：并发轮换只有一个请求成功。 */
    int revokeById(@Param("tokenId") String tokenId, @Param("reason") String reason,
                   @Param("revokedAt") LocalDateTime revokedAt);

    /** 作用：按摘要撤销仍有效 Token。输入：摘要、撤销原因。输出：受影响行数。逻辑：供退出判断 Token 是否可撤销。 */
    int revokeByHash(@Param("tokenHash") String tokenHash, @Param("reason") String reason,
                     @Param("revokedAt") LocalDateTime revokedAt);


    /** 用户公开资料行，供注册、资料读取和认证响应转换复用。 */
    record UserRow(String id, String email, String nickname, String avatarObjectKey, String role, String status,
                   boolean onboardingCompleted, boolean mustChangePassword, int authVersion) {
    }

    /** 登录查询行，在公开资料基础上额外携带仅供密码校验的哈希。 */
    record UserWithPassword(String id, String email, String passwordHash, String nickname, String avatarObjectKey,
                            String role, String status, boolean onboardingCompleted, boolean mustChangePassword,
                            int authVersion) {
    }

    /** Refresh Token 校验行，包含 Token 生命周期与所属用户的角色、状态。 */
    record RefreshTokenRow(String id, String userId, Instant expiresAt, Instant revokedAt, String role, String status,
                           int authVersion) {
    }

    /** 新 Refresh Token 的持久化字段，只保存摘要和父 Token 关联。 */
    record RefreshTokenInsert(String id, String userId, String tokenHash, String parentTokenId, Instant expiresAt) {
    }

}

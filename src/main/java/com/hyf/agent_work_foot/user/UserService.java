package com.hyf.agent_work_foot.user;

import com.hyf.agent_work_foot.auth.mapper.AuthMapper.UserRow;
import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.preference.PreferenceRequests;
import com.hyf.agent_work_foot.preference.PreferenceService;
import com.hyf.agent_work_foot.user.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 当前用户资料和引导状态的业务服务。
 *
 * <p>编排用户 Mapper 与偏好服务，保证资料变更只作用于 JWT 已认证的用户；不直接处理 HTTP 或 SQL。</p>
 */
@Service
public class UserService {
    private final UserMapper mapper;
    private final PreferenceService preferenceService;

    /** 作用：注入用户数据访问和偏好业务服务。输入：UserMapper、PreferenceService。输出：服务实例。逻辑：保存依赖。 */
    public UserService(UserMapper mapper, PreferenceService preferenceService) {
        this.mapper = mapper;
        this.preferenceService = preferenceService;
    }

    /**
     * 作用：读取当前用户资料。
     *
     * <p>输入：JWT 提取的用户 ID。输出：可公开用户资料；用户不存在时抛 Token 无效错误。
     * 逻辑：先确保用户存在，再将 Mapper 行转换为模块响应 DTO。</p>
     */
    public UserResponses.UserData currentUser(String id) {
        return data(required(id));
    }

    /**
     * 作用：更新当前用户昵称。
     *
     * <p>输入：JWT 用户 ID 和校验过的资料修改请求。输出：更新后的资料。
     * 逻辑：同一事务内去除昵称首尾空格、更新数据库并重新读取结果。</p>
     */
    @Transactional
    public UserResponses.UserData updateProfile(String id, UserRequests.ProfilePatchRequest request) {
        mapper.updateNickname(id, request.nickname().trim());
        return currentUser(id);
    }

    /**
     * 作用：完成当前用户首次引导。
     *
     * <p>输入：JWT 用户 ID 和完整引导请求。输出：已完成引导的资料。
     * 逻辑：同一事务内提交偏好预算、按需更新昵称、标记引导完成，任何失败都回滚。</p>
     */
    @Transactional
    public UserResponses.UserData completeOnboarding(String id, PreferenceRequests.OnboardingRequest request) {
        preferenceService.submitOnboarding(id, request);
        if (request.nickname() != null) {
            mapper.updateNickname(id, request.nickname().trim());
        }
        mapper.completeOnboarding(id);
        return currentUser(id);
    }

    /**
     * 作用：读取必须存在的当前用户。
     *
     * <p>输入：JWT 用户 ID。输出：用户行。逻辑：查询为空时将已失效账户视为无效登录状态，不泄露查询细节。</p>
     */
    private UserRow required(String id) {
        UserRow user = mapper.selectById(id);
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID", "登录状态无效");
        }
        return user;
    }

    /**
     * 作用：转换用户持久化行到 HTTP 响应。
     *
     * <p>输入：已查询到的用户行。输出：公开用户资料。逻辑：显式挑选可公开字段，不暴露密码或 Token 数据。</p>
     */
    private UserResponses.UserData data(UserRow user) {
        return new UserResponses.UserData(
                user.id(),
                user.email(),
                user.nickname(),
                user.avatarObjectKey(),
                user.role(),
                user.status(),
                user.onboardingCompleted(),
                user.mustChangePassword()
        );
    }
}

package com.hyf.agent_work_foot.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用户模块的请求 DTO 集合。
 *
 * <p>描述用户资料接口可接受的字段及格式，不负责当前用户身份和持久化。</p>
 */
public final class UserRequests {
    /** 作用：禁止创建 DTO 容器实例。输入：无。输出：无。逻辑：仅通过嵌套 record 使用。 */
    private UserRequests() {
    }

    /** 资料更新请求：昵称不能为空，最大长度为 20。 */
    public record ProfilePatchRequest(@NotBlank @Size(max = 20) String nickname) {
    }
}

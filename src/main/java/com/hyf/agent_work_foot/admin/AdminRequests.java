package com.hyf.agent_work_foot.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Admin接口请求DTO集合，只声明HTTP输入格式，不承载状态转换规则。 */
public final class AdminRequests {
    /** 作用：禁止实例化DTO容器。输入：无。输出：无。逻辑：请求类型通过嵌套record访问。 */
    private AdminRequests() {
    }

    /** 账号状态PATCH请求；仅允许ACTIVE或DISABLED，未知JSON字段由全局Jackson配置拒绝。 */
    public record StatusPatchRequest(
            @NotBlank(message = "状态不能为空")
            @Pattern(regexp = "ACTIVE|DISABLED", message = "状态只能是ACTIVE或DISABLED")
            String status
    ) {
    }
}

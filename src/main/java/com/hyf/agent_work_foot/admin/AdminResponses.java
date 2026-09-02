package com.hyf.agent_work_foot.admin;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Admin接口响应DTO集合，只暴露最小账号资料和一次性临时密码响应。 */
public final class AdminResponses {
    /** 作用：禁止实例化DTO容器。输入：无。输出：无。逻辑：响应类型通过嵌套record访问。 */
    private AdminResponses() {
    }

    /** 管理员用户列表项，不包含密码、Token或用户业务数据。 */
    public record AdminUserData(
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

    /** 0基管理员分页响应。 */
    public record AdminUserPageData(
            List<AdminUserData> items,
            int page,
            int size,
            long totalElements,
            long totalPages
    ) {
    }

    /** 临时密码只在本次响应中存在，调用方不得记录。 */
    public record TemporaryPasswordData(String temporaryPassword, Instant expiresAt) {
    }

    /** 审计记录中的管理员识别信息，不返回内部UUID、邮箱或用户业务数据。 */
    public record AuditAdminData(String account, String nickname) {
    }

    /** 审计记录中的目标用户识别信息，不返回内部UUID或用户业务数据。 */
    public record AuditTargetUserData(String email, String nickname) {
    }

    /** 管理员审计列表项；detail只含后端白名单字段。 */
    public record AdminAuditLogData(
            String id,
            AuditAdminData admin,
            AuditTargetUserData targetUser,
            String action,
            String result,
            String requestId,
            Map<String, String> detail,
            Instant createdAt
    ) {
    }

    /** 0基管理员审计分页响应。 */
    public record AdminAuditLogPageData(
            List<AdminAuditLogData> items,
            int page,
            int size,
            long totalElements,
            long totalPages
    ) {
    }
}

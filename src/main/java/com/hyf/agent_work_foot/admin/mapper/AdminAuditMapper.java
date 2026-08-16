package com.hyf.agent_work_foot.admin.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

/** 管理员审计数据访问接口，只写入非敏感操作摘要。 */
public interface AdminAuditMapper {
    /** 作用：插入审计记录。输入：完整审计字段。输出：无。逻辑：detailJson由数据库转换为JSON，禁止拼接SQL。 */
    void insertAudit(@Param("audit") AdminAuditInsert audit);

    /** 作用：统计目标账号指定动作的审计数量。输入：目标和动作。输出：数量。逻辑：供内部核验和自动化测试，不暴露HTTP。 */
    long countByTargetAndAction(@Param("targetUserId") String targetUserId,
                                @Param("action") String action);

    /** 审计插入模型，detailJson不得包含邮箱、昵称、密码、Token和用户业务数据。 */
    record AdminAuditInsert(
            String id,
            String adminUserId,
            String targetUserId,
            String action,
            String result,
            String requestId,
            String detailJson,
            LocalDateTime createdAt
    ) {
    }
}

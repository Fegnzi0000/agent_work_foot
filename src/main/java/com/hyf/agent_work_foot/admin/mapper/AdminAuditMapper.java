package com.hyf.agent_work_foot.admin.mapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 管理员审计数据访问接口，只写入非敏感操作摘要。 */
public interface AdminAuditMapper {
    /** 作用：插入审计记录。输入：完整审计字段。输出：无。逻辑：detailJson由数据库转换为JSON，禁止拼接SQL。 */
    void insertAudit(@Param("audit") AdminAuditInsert audit);

    /** 作用：统计目标账号指定动作的审计数量。输入：目标和动作。输出：数量。逻辑：供内部核验和自动化测试，不暴露HTTP。 */
    long countByTargetAndAction(@Param("targetUserId") String targetUserId,
                                @Param("action") String action);

    /** 作用：分页读取审计记录。输入：已校验的筛选与分页。输出：按时间倒序的最小审计投影。 */
    List<AdminAuditLogRow> selectAuditLogPage(@Param("query") AdminAuditLogQuery query);

    /** 作用：统计审计筛选总数。输入：与列表相同的筛选条件。输出：总记录数。 */
    long countAuditLogs(@Param("query") AdminAuditLogQuery query);

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

    /** 审计查询条件；时间为UTC的左闭右开区间，offset为0基行偏移。 */
    record AdminAuditLogQuery(
            String adminAccount,
            String targetUserEmail,
            String targetUserNickname,
            String action,
            String result,
            LocalDateTime startAt,
            LocalDateTime endAt,
            int offset,
            int size
    ) {
    }

    /** 审计列表平面投影；账号字段可因历史账号不存在而为空。 */
    record AdminAuditLogRow(
            String id,
            String adminAccount,
            String adminNickname,
            String targetUserId,
            String targetEmail,
            String targetNickname,
            String action,
            String result,
            String requestId,
            String detailJson,
            Instant createdAt
    ) {
    }
}

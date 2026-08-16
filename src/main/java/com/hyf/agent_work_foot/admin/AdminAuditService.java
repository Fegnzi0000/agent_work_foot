package com.hyf.agent_work_foot.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyf.agent_work_foot.admin.mapper.AdminAuditMapper;
import com.hyf.agent_work_foot.common.RequestIdContext;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 管理员审计服务，成功事件加入业务事务，业务拒绝使用独立事务保留。 */
@Service
public class AdminAuditService {
    private final AdminAuditMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** 作用：注入审计持久化依赖。输入：Mapper、JSON序列化器和时钟。输出：服务实例。逻辑：只接受调用方提供的非敏感摘要。 */
    public AdminAuditService(AdminAuditMapper mapper, ObjectMapper objectMapper, Clock clock) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** 作用：记录成功或幂等事件。输入：管理员、目标、动作和摘要。输出：无。逻辑：默认加入AdminService当前事务。 */
    public void success(String adminUserId, String targetUserId, String action, Map<String, Object> detail) {
        insert(adminUserId, targetUserId, action, "SUCCESS", detail);
    }

    /** 作用：记录业务拒绝事件。输入：管理员、可空目标、动作和原因。输出：无。逻辑：独立提交，不随外层业务异常回滚。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(String adminUserId, String targetUserId, String action, String reason) {
        insert(adminUserId, targetUserId, action, "FAILURE", Map.of("reason", reason));
    }

    /** 作用：转换并写入审计。输入：审计字段。输出：无。逻辑：统一绑定当前requestId和UTC时间，序列化失败中止操作。 */
    private void insert(String adminUserId, String targetUserId, String action,
                        String result, Map<String, Object> detail) {
        try {
            mapper.insertAudit(new AdminAuditMapper.AdminAuditInsert(
                    UUID.randomUUID().toString(),
                    adminUserId,
                    targetUserId,
                    action,
                    result,
                    RequestIdContext.current(),
                    objectMapper.writeValueAsString(detail),
                    LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化管理员审计摘要", exception);
        }
    }
}

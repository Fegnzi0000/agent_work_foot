package com.hyf.agent_work_foot.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.agent_work_foot.admin.entity.TemporaryPasswordEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

/** 临时密码数据访问接口，负责单表写入、登录行锁和一次性消费。 */
public interface TemporaryPasswordMapper extends BaseMapper<TemporaryPasswordEntity> {
    /** 作用：锁定用户最近一次临时密码。输入：用户ID。输出：凭据状态或空。逻辑：登录与管理员重复生成不能交叉消费。 */
    TemporaryPasswordRow selectLatestForUpdate(@Param("userId") String userId);

    /** 作用：原子消费临时密码。输入：记录ID和UTC时间。输出：影响行数。逻辑：只有未使用、未撤销、未过期记录可成功一次。 */
    int markUsed(@Param("id") String id, @Param("usedAt") LocalDateTime usedAt);

    /** 登录校验所需临时密码投影，密码字段仅为BCrypt哈希。 */
    record TemporaryPasswordRow(
            String id,
            String userId,
            String passwordHash,
            LocalDateTime expiresAt,
            LocalDateTime usedAt,
            LocalDateTime revokedAt
    ) {
    }
}

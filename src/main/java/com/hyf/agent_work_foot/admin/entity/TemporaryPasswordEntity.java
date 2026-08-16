package com.hyf.agent_work_foot.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** temporary_passwords实体，只保存哈希和生命周期，严禁保存临时密码明文。 */
@Getter
@Setter
@TableName("temporary_passwords")
public class TemporaryPasswordEntity {
    @TableId
    private String id;
    private String userId;
    private String passwordHash;
    private String createdByAdminId;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private LocalDateTime revokedAt;
    private LocalDateTime createdAt;
}

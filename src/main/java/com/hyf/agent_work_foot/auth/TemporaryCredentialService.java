package com.hyf.agent_work_foot.auth;

import com.hyf.agent_work_foot.admin.mapper.TemporaryPasswordMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** 临时凭据认证服务，负责强制改密账号的一次性密码校验和原子消费。 */
@Service
public class TemporaryCredentialService {
    private final TemporaryPasswordMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    /** 作用：注入临时密码数据访问、编码器和时钟。输入：Mapper、PasswordEncoder、Clock。输出：服务实例。逻辑：不接触HTTP响应。 */
    public TemporaryCredentialService(
            TemporaryPasswordMapper mapper,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** 作用：验证正式密码或严格消费临时密码。输入：用户、当前哈希、强制改密标记和原文。输出：是否认证成功。逻辑：临时密码必须同时匹配用户哈希和最新有效记录，且markUsed只能成功一次。 */
    public boolean authenticate(
            String userId,
            String currentPasswordHash,
            boolean mustChangePassword,
            String presentedPassword
    ) {
        if (!mustChangePassword) {
            return passwordEncoder.matches(presentedPassword, currentPasswordHash);
        }
        TemporaryPasswordMapper.TemporaryPasswordRow temporary = mapper.selectLatestForUpdate(userId);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (temporary == null
                || temporary.usedAt() != null
                || temporary.revokedAt() != null
                || !temporary.expiresAt().isAfter(now)
                || !passwordEncoder.matches(presentedPassword, currentPasswordHash)
                || !passwordEncoder.matches(presentedPassword, temporary.passwordHash())) {
            return false;
        }
        return mapper.markUsed(temporary.id(), now) == 1;
    }
}

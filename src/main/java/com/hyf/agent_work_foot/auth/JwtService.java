package com.hyf.agent_work_foot.auth;

import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.config.AuthProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/**
 * HS256 Access Token 的签发和校验服务。
 *
 * <p>仅使用活动密钥签发，按 JWT Header 中的 kid 选择活动或上一把密钥校验，以支持短期密钥轮换；不管理 Refresh Token。</p>
 */
@Service
public class JwtService {
    private final AuthProperties.Jwt properties;
    private final JwtEncoder encoder;
    private final Map<String, SecretKey> verificationKeys;

    /**
     * 作用：根据外部配置初始化 JWT 编码器和密钥环。
     *
     * <p>输入：认证配置中的 JWT 子配置。输出：可签发活动 Token、验证两把密钥 Token 的服务实例。
     * 逻辑：活动密钥同时配置编码和解码器，上一把密钥只加入解码器。</p>
     */
    public JwtService(AuthProperties properties) {
        this.properties = properties.jwt();
        SecretKey activeKey = key(this.properties.activeSecret());
        OctetSequenceKey activeJwk = new OctetSequenceKey.Builder(activeKey)
                .keyID(this.properties.activeKeyId())
                .algorithm(JWSAlgorithm.HS256)
                .build();
        this.encoder = new NimbusJwtEncoder(
                new ImmutableJWKSet<SecurityContext>(new JWKSet(activeJwk))
        );
        this.verificationKeys = new LinkedHashMap<>();
        verificationKeys.put(this.properties.activeKeyId(), activeKey);
        if (present(this.properties.previousKeyId()) && present(this.properties.previousSecret())) {
            verificationKeys.put(this.properties.previousKeyId(), key(this.properties.previousSecret()));
        }
    }

    /**
     * 作用：使用活动密钥签发 Access Token。
     *
     * <p>输入：已认证用户 ID 与角色。输出：带 kid、过期时间和访问类型声明的 JWT 字符串。
     * 逻辑：写入 issuer、subject、role、type、签发时间与过期时间，不签发 Refresh Token。</p>
     */
    public String issueAccessToken(String userId, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(userId)
                .claim("role", role)
                .claim("type", "access")
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).keyId(properties.activeKeyId()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * 作用：校验 Access Token 并提取认证主体。
     *
     * <p>输入：客户端提交的 JWT 原文。输出：用户 ID 和角色；校验失败抛 AUTH_TOKEN_INVALID。
     * 逻辑：先读取 kid 选择密钥，再验证签名、issuer、type、subject 和 role，防止非 Access Token 混用。</p>
     */
    public AccessToken verifyAccessToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            String keyId = jwt.getHeader().getKeyID();
            SecretKey verificationKey = verificationKeys.get(keyId);
            if (verificationKey == null
                    || !JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
                    || !jwt.verify(new MACVerifier(verificationKey))) {
                throw unauthorized();
            }
            var claims = jwt.getJWTClaimsSet();
            if (!properties.issuer().equals(claims.getIssuer())
                    || !"access".equals(claims.getStringClaim("type"))
                    || claims.getExpirationTime() == null
                    || !claims.getExpirationTime().toInstant().isAfter(Instant.now())) {
                throw unauthorized();
            }
            String role = claims.getStringClaim("role");
            if (claims.getSubject() == null || role == null) {
                throw unauthorized();
            }
            return new AccessToken(claims.getSubject(), role);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unauthorized();
        }
    }

    /** 作用：把配置密钥转为 HMAC 密钥。输入：文本密钥。输出：SecretKey。逻辑：拒绝空值和短于 32 字符的密钥。 */
    private SecretKey key(String secret) {
        if (!present(secret) || secret.length() < 32) {
            throw new IllegalStateException("JWT 密钥至少需要 32 个字符");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** 作用：判断配置文本是否有效。输入：可为空文本。输出：是否非空白。逻辑：统一密钥配置判定。 */
    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    /** 作用：创建 Token 无效异常。输入：无。输出：AUTH_TOKEN_INVALID 异常。逻辑：隐藏签名、解析等内部失败原因。 */
    private ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID", "Token无效或已过期");
    }

    /** 已验证 Access Token 的最小认证信息，供安全 Filter 写入 Security Context。 */
    public record AccessToken(String userId, String role) {
    }
}

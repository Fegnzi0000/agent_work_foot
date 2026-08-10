package com.hyf.agent_work_foot.auth;

import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.config.AuthProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final AuthProperties.Jwt properties;
    private final JwtEncoder encoder;
    private final Map<String, JwtDecoder> decoders;

    public JwtService(AuthProperties properties) {
        this.properties = properties.jwt();
        SecretKey activeKey = key(this.properties.activeSecret());
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(activeKey));
        this.decoders = new LinkedHashMap<>();
        decoders.put(this.properties.activeKeyId(), decoder(activeKey));
        if (present(this.properties.previousKeyId()) && present(this.properties.previousSecret())) {
            decoders.put(this.properties.previousKeyId(), decoder(key(this.properties.previousSecret())));
        }
    }

    public String issueAccessToken(String userId, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(properties.issuer()).subject(userId)
                .claim("role", role).claim("type", "access").issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl())).build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).keyId(properties.activeKeyId()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public AccessToken verifyAccessToken(String token) {
        try {
            String keyId = SignedJWT.parse(token).getHeader().getKeyID();
            JwtDecoder decoder = decoders.get(keyId);
            if (decoder == null) throw unauthorized();
            Jwt jwt = decoder.decode(token);
            if (!properties.issuer().equals(jwt.getIssuer() == null ? null : jwt.getIssuer().toString())
                    || !"access".equals(jwt.getClaimAsString("type"))) throw unauthorized();
            String role = jwt.getClaimAsString("role");
            if (jwt.getSubject() == null || role == null) throw unauthorized();
            return new AccessToken(jwt.getSubject(), role);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unauthorized();
        }
    }

    private JwtDecoder decoder(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private SecretKey key(String secret) {
        if (!present(secret) || secret.length() < 32) throw new IllegalStateException("JWT 密钥至少需要 32 个字符");
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private boolean present(String value) { return value != null && !value.isBlank(); }
    private ApiException unauthorized() { return new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID", "Token无效或已过期"); }
    public record AccessToken(String userId, String role) { }
}

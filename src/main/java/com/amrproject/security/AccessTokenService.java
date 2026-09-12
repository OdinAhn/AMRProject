package com.amrproject.security;

import com.amrproject.domain.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * 자체 서명 접근 토큰 (10단계). 표준 JWT 라이브러리를 추가하는 대신
 * HMAC-SHA256으로 직접 서명한다 — 이 서버가 발급과 검증을 모두 수행하고
 * 외부 시스템과의 상호운용이 필요 없어 스펙 준수를 약속할 이유가 없다
 * (docs/AGENTS.md: 작은 코드로 되는 걸 위해 라이브러리를 추가하지 않는다).
 * 형식: base64url(payload JSON) + "." + base64url(HMAC-SHA256 서명).
 */
@Service
public class AccessTokenService {

    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final JsonMapper jsonMapper;
    private final SecretKeySpec key;
    private final long expiryMinutes;

    public AccessTokenService(
            JsonMapper jsonMapper,
            @Value("${app.token.secret}") String secret,
            @Value("${app.token.expiry-minutes:480}") long expiryMinutes
    ) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "app.token.secret이 비어 있습니다 — TOKEN_SIGNING_SECRET 환경변수를 설정하세요."
            );
        }
        this.jsonMapper = jsonMapper;
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        this.expiryMinutes = expiryMinutes;
    }

    public String issueToken(User user) {
        long expiresAt = Instant.now().getEpochSecond() + expiryMinutes * 60;
        Map<String, Object> payload = Map.of(
                "sub", user.getId(),
                "username", user.getUsername(),
                "role", user.getRole().getCode(),
                "exp", expiresAt
        );
        String encodedPayload = ENCODER.encodeToString(jsonMapper.writeValueAsBytes(payload));
        String signature = ENCODER.encodeToString(sign(encodedPayload));
        return encodedPayload + "." + signature;
    }

    public Optional<TokenPayload> parseToken(String token) {
        int separatorIndex = token.lastIndexOf('.');
        if (separatorIndex < 0) {
            return Optional.empty();
        }
        String encodedPayload = token.substring(0, separatorIndex);
        String signature = token.substring(separatorIndex + 1);
        try {
            byte[] expectedSignature = sign(encodedPayload);
            if (!java.security.MessageDigest.isEqual(expectedSignature, DECODER.decode(signature))) {
                return Optional.empty();
            }
            Map<?, ?> payload = jsonMapper.readValue(DECODER.decode(encodedPayload), Map.class);
            long expiresAt = ((Number) payload.get("exp")).longValue();
            if (Instant.now().getEpochSecond() > expiresAt) {
                return Optional.empty();
            }
            return Optional.of(new TokenPayload(
                    ((Number) payload.get("sub")).longValue(),
                    (String) payload.get("username"),
                    (String) payload.get("role")
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private byte[] sign(String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("토큰 서명에 실패했습니다.", e);
        }
    }

    public record TokenPayload(Long userId, String username, String role) {
    }
}

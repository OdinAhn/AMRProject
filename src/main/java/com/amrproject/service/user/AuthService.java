package com.amrproject.service.user;

import com.amrproject.domain.entity.user.User;
import com.amrproject.domain.entity.user.UserStatus;
import com.amrproject.exception.AuthenticationFailedException;
import com.amrproject.exception.LoginRateLimitedException;
import com.amrproject.repository.user.UserRepository;
import com.amrproject.security.AccessTokenService;
import com.amrproject.service.event.AuditLogService;
import com.amrproject.service.event.AuditLogService.AuditEntry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final AuditLogService auditLogService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AccessTokenService accessTokenService,
            LoginAttemptLimiter loginAttemptLimiter,
            AuditLogService auditLogService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.auditLogService = auditLogService;
    }

    /**
     * 존재하지 않는 사용자/틀린 비밀번호를 구분하지 않고 같은 오류로 응답한다
     * (사용자명 존재 여부 노출 방지).
     *
     * <p>36단계: 실패가 쌓인 아이디·IP는 비밀번호를 확인하기 전에 429로 막는다(맞는 비밀번호여도). 성공하면 아이디 카운터를 지운다.
     * <p>37단계: 결과마다 감사 로그를 남긴다. 응답은 실패 사유를 숨기지만 감사 로그(관리자만 조회)에는 사유를 적는다.
     */
    public LoginResult login(String username, String password, String clientIp) {
        try {
            loginAttemptLimiter.checkAllowed(username, clientIp);
        } catch (LoginRateLimitedException e) {
            audit(null, username, "AUTH_LOGIN_RATE_LIMITED", 429, Map.of("retryAfterSeconds", e.getRetryAfterSeconds()), clientIp);
            throw e;
        }
        User user = userRepository.findByUsername(username).orElse(null);
        String failureReason = user == null ? "UNKNOWN_USERNAME"
                : user.getStatus() != UserStatus.ACTIVE ? "USER_NOT_ACTIVE"
                : !passwordEncoder.matches(password, user.getPasswordHash()) ? "WRONG_PASSWORD"
                : null;
        if (failureReason != null) {
            loginAttemptLimiter.recordFailure(username, clientIp);
            audit(user, username, "AUTH_LOGIN_FAILED", 401, Map.of("reason", failureReason), clientIp);
            throw new AuthenticationFailedException("INVALID_CREDENTIALS", "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        loginAttemptLimiter.clearUsername(username);
        audit(user, user.getUsername(), "AUTH_LOGIN_SUCCEEDED", 200, Map.of(), clientIp);
        return new LoginResult(accessTokenService.issueToken(user), user);
    }

    private void audit(User user, String username, String action, int status, Map<String, Object> payload, String clientIp) {
        // 요청 아이디는 길이 제한이 없어 컬럼(100자)에 맞게 자른다
        String actorUsername = username.length() > 100 ? username.substring(0, 100) : username;
        Long userId = user != null ? user.getId() : null;
        auditLogService.record(new AuditEntry(userId, actorUsername, action, userId != null ? "USER" : null, userId, status, payload, clientIp));
    }

    /** 32단계: 비밀번호 변경 직후처럼 이미 확인된 사용자에게 새 토큰을 발급한다. */
    public String issueToken(User user) {
        return accessTokenService.issueToken(user);
    }

    public record LoginResult(String token, User user) {
    }
}

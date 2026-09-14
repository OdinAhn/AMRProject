package com.amrproject.service.user;

import com.amrproject.domain.entity.user.User;
import com.amrproject.domain.entity.user.UserStatus;
import com.amrproject.exception.AuthenticationFailedException;
import com.amrproject.repository.user.UserRepository;
import com.amrproject.security.AccessTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AccessTokenService accessTokenService,
            LoginAttemptLimiter loginAttemptLimiter
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    /**
     * 존재하지 않는 사용자/틀린 비밀번호를 구분하지 않고 같은 오류로 응답한다
     * (사용자명 존재 여부 노출 방지).
     *
     * <p>36단계: 실패가 쌓인 아이디·IP는 비밀번호를 확인하기 전에 429로 막는다(맞는 비밀번호여도). 성공하면 아이디 카운터를 지운다.
     */
    public LoginResult login(String username, String password, String clientIp) {
        loginAttemptLimiter.checkAllowed(username, clientIp);
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE || !passwordEncoder.matches(password, user.getPasswordHash())) {
            loginAttemptLimiter.recordFailure(username, clientIp);
            throw new AuthenticationFailedException("INVALID_CREDENTIALS", "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        loginAttemptLimiter.clearUsername(username);
        return new LoginResult(accessTokenService.issueToken(user), user);
    }

    /** 32단계: 비밀번호 변경 직후처럼 이미 확인된 사용자에게 새 토큰을 발급한다. */
    public String issueToken(User user) {
        return accessTokenService.issueToken(user);
    }

    public record LoginResult(String token, User user) {
    }
}

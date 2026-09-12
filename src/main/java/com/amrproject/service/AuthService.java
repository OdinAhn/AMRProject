package com.amrproject.service;

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

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, AccessTokenService accessTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
    }

    /**
     * 존재하지 않는 사용자/틀린 비밀번호를 구분하지 않고 같은 오류로 응답한다
     * (사용자명 존재 여부 노출 방지).
     */
    public LoginResult login(String username, String password) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AuthenticationFailedException("INVALID_CREDENTIALS", "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        return new LoginResult(accessTokenService.issueToken(user), user);
    }

    public record LoginResult(String token, User user) {
    }
}

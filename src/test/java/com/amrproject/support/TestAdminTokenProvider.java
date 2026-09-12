package com.amrproject.support;

import com.amrproject.domain.entity.Role;
import com.amrproject.domain.entity.User;
import com.amrproject.repository.RoleRepository;
import com.amrproject.repository.UserRepository;
import com.amrproject.security.AccessTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 테스트용 관리자 토큰 발급을 하나의 트랜잭션 안에서 끝낸다. User.role은
 * FetchType.LAZY라, 트랜잭션 밖(예: @Bean 팩토리 메서드)에서 조회한 User를
 * 넘기면 AccessTokenService.issueToken이 role에 접근할 때 세션이 이미
 * 닫혀 있어 LazyInitializationException이 난다 — 여기서 문자열(토큰)만
 * 반환해 그 위험을 없앤다.
 */
@Component
public class TestAdminTokenProvider {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;

    public TestAdminTokenProvider(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AccessTokenService accessTokenService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
    }

    @Transactional
    public String issueAdminToken() {
        Role adminRole = roleRepository.findByCode("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN 역할이 시드돼 있지 않습니다 (V3__seed_roles.sql)."));
        User admin = userRepository.findByUsername("test-admin")
                .orElseGet(() -> userRepository.save(new User(
                        "test-admin", passwordEncoder.encode("test-admin-password"), "테스트 관리자", adminRole
                )));
        return accessTokenService.issueToken(admin);
    }
}

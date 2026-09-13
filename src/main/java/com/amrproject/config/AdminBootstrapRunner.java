package com.amrproject.config;

import com.amrproject.domain.entity.user.Role;
import com.amrproject.domain.entity.user.User;
import com.amrproject.repository.user.RoleRepository;
import com.amrproject.repository.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 26단계: 최초 관리자 계정 부트스트랩. 사용자 생성 API는 ADMIN 권한이 필요해 빈 DB에서는
 * API만으로 첫 관리자를 만들 수 없었다. {@code BOOTSTRAP_ADMIN_USERNAME}/{@code BOOTSTRAP_ADMIN_PASSWORD}가
 * 둘 다 설정돼 있고 ADMIN 역할 사용자가 한 명도 없을 때만 생성한다 — 이미 관리자가 있으면
 * 아무것도 하지 않으므로 환경 변수를 계속 남겨둬도 재기동 때 계정이 덮어써지지 않는다.
 * 비밀번호는 코드나 설정 파일에 두지 않고 로그에도 남기지 않는다.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    private static final String ADMIN_ROLE = "ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminBootstrapRunner(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin.username:}") String username,
            @Value("${app.bootstrap.admin.password:}") String password
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username.isBlank() || password.isBlank()) {
            return;
        }
        if (userRepository.existsByRoleCode(ADMIN_ROLE)) {
            return;
        }
        if (userRepository.existsByUsername(username)) {
            log.warn("최초 관리자 부트스트랩을 건너뜁니다 — username={}가 이미 ADMIN이 아닌 역할로 존재합니다.", username);
            return;
        }
        Role adminRole = roleRepository.findByCode(ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException("ADMIN 역할이 시드돼 있지 않습니다 (V3__seed_roles.sql)."));
        userRepository.save(new User(username, passwordEncoder.encode(password), "관리자", adminRole));
        log.info("최초 관리자 계정을 생성했습니다: username={}", username);
    }
}

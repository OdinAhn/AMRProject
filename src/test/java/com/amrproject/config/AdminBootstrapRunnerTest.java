package com.amrproject.config;

import com.amrproject.domain.entity.user.Role;
import com.amrproject.domain.entity.user.User;
import com.amrproject.repository.user.RoleRepository;
import com.amrproject.repository.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 26단계: 최초 관리자 부트스트랩 조건을 DB 없이(Docker 불필요) 검증한다.
 */
class AdminBootstrapRunnerTest {

    private final UserRepository userRepository = Mockito.mock(UserRepository.class);
    private final RoleRepository roleRepository = Mockito.mock(RoleRepository.class);
    private final PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);

    @Test
    void 환경변수가_둘_다_있고_관리자가_없으면_관리자를_생성한다() {
        Role adminRole = Mockito.mock(Role.class);
        when(userRepository.existsByRoleCode("ADMIN")).thenReturn(false);
        when(userRepository.existsByUsername("root")).thenReturn(false);
        when(roleRepository.findByCode("ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode("secret-password")).thenReturn("hashed");

        runner("root", "secret-password").run(new DefaultApplicationArguments());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("root");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getValue().getRole()).isSameAs(adminRole);
    }

    @Test
    void 이미_관리자가_있으면_아무것도_하지_않는다() {
        when(userRepository.existsByRoleCode("ADMIN")).thenReturn(true);

        runner("root", "secret-password").run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any());
    }

    @Test
    void 환경변수가_하나라도_비어_있으면_아무것도_하지_않는다() {
        runner("root", "").run(new DefaultApplicationArguments());
        runner("", "secret-password").run(new DefaultApplicationArguments());

        verify(userRepository, never()).existsByRoleCode(any());
        verify(userRepository, never()).save(any());
    }

    private AdminBootstrapRunner runner(String username, String password) {
        return new AdminBootstrapRunner(userRepository, roleRepository, passwordEncoder, username, password);
    }
}

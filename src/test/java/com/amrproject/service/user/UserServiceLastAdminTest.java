package com.amrproject.service.user;

import com.amrproject.domain.entity.user.Role;
import com.amrproject.domain.entity.user.User;
import com.amrproject.domain.entity.user.UserStatus;
import com.amrproject.dto.request.user.UpdateUserRequest;
import com.amrproject.exception.InvalidStateTransitionException;
import com.amrproject.repository.user.RoleRepository;
import com.amrproject.repository.user.UserRepository;
import com.amrproject.repository.user.UserStationAssignmentRepository;
import com.amrproject.service.site.StationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 32단계: 마지막 활성 관리자 보호 규칙. 통합 테스트 DB에는 다른 테스트가 만든 관리자가 여러 명 있어
 * "마지막 한 명" 상황을 만들 수 없으므로 저장소를 흉내 내 검증한다(Docker 불필요).
 */
class UserServiceLastAdminTest {

    private final UserRepository userRepository = Mockito.mock(UserRepository.class);
    private final RoleRepository roleRepository = Mockito.mock(RoleRepository.class);
    private final UserService userService = new UserService(
            userRepository, roleRepository, Mockito.mock(PasswordEncoder.class),
            Mockito.mock(UserStationAssignmentRepository.class), Mockito.mock(StationService.class),
            Mockito.mock(LoginAttemptLimiter.class)
    );

    private Role adminRole;
    private Role workerRole;
    private User admin;

    @BeforeEach
    void setUp() {
        adminRole = role("ADMIN");
        workerRole = role("WORKER");
        admin = new User("only-admin", "hash", "관리자", adminRole);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(roleRepository.findByCode("WORKER")).thenReturn(Optional.of(workerRole));
    }

    @Test
    void 마지막_활성_관리자를_작업자로_바꾸거나_비활성화하면_409() {
        when(userRepository.countByRoleCodeAndStatus("ADMIN", UserStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> userService.update(1L, new UpdateUserRequest(null, null, "WORKER")))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("마지막 활성 관리자");
        assertThatThrownBy(() -> userService.update(1L, new UpdateUserRequest(null, UserStatus.DISABLED, null)))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(admin.getRole()).isSameAs(adminRole);
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void 다른_활성_관리자가_있으면_강등할_수_있고_이름만_바꾸는_것은_항상_된다() {
        when(userRepository.countByRoleCodeAndStatus("ADMIN", UserStatus.ACTIVE)).thenReturn(1L);
        userService.update(1L, new UpdateUserRequest("새 이름", null, null));
        assertThat(admin.getDisplayName()).isEqualTo("새 이름");

        when(userRepository.countByRoleCodeAndStatus("ADMIN", UserStatus.ACTIVE)).thenReturn(2L);
        userService.update(1L, new UpdateUserRequest(null, null, "WORKER"));
        assertThat(admin.getRole()).isSameAs(workerRole);
    }

    private static Role role(String code) {
        Role role = Mockito.mock(Role.class);
        when(role.getCode()).thenReturn(code);
        return role;
    }
}

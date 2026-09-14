package com.amrproject.service.user;

import com.amrproject.domain.entity.site.Station;
import com.amrproject.domain.entity.user.Role;
import com.amrproject.domain.entity.user.User;
import com.amrproject.domain.entity.user.UserStationAssignment;
import com.amrproject.dto.request.user.CreateUserRequest;
import com.amrproject.dto.request.user.UpdateUserRequest;
import com.amrproject.exception.DuplicateResourceException;
import com.amrproject.domain.entity.user.UserStatus;
import com.amrproject.exception.EntityNotFoundException;
import com.amrproject.exception.InvalidRequestException;
import com.amrproject.exception.InvalidStateTransitionException;
import com.amrproject.repository.user.RoleRepository;
import com.amrproject.repository.user.UserRepository;
import com.amrproject.repository.user.UserStationAssignmentRepository;
import com.amrproject.service.site.StationService;
import java.time.Instant;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserStationAssignmentRepository userStationAssignmentRepository;
    private final StationService stationService;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            UserStationAssignmentRepository userStationAssignmentRepository,
            StationService stationService,
            LoginAttemptLimiter loginAttemptLimiter
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.userStationAssignmentRepository = userStationAssignmentRepository;
        this.stationService = stationService;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    @Transactional
    public User create(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("USERNAME_DUPLICATE", "이미 존재하는 username입니다: " + request.username());
        }
        Role role = roleRepository.findByCode(request.roleCode())
                .orElseThrow(() -> new EntityNotFoundException("ROLE_NOT_FOUND", "역할을 찾을 수 없습니다: " + request.roleCode()));
        String encodedPassword = passwordEncoder.encode(request.password());
        return userRepository.save(new User(request.username(), encodedPassword, request.displayName(), role));
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다: " + id));
    }

    public List<User> getAll() {
        return userRepository.findAll();
    }

    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다: " + username));
    }

    /**
     * 32단계: 역할도 바꿀 수 있다. 활성 관리자를 강등하거나 비활성화해 활성 관리자가 한 명도 남지 않게 되는 변경은
     * 409 {@code LAST_ACTIVE_ADMIN}으로 막는다 — 막지 않으면 API로는 되돌릴 방법이 없어진다(26단계 부트스트랩은 관리자가 0명일 때만 동작).
     */
    @Transactional
    public User update(Long id, UpdateUserRequest request) {
        User user = getById(id);
        Role targetRole = request.roleCode() != null ? findRole(request.roleCode()) : user.getRole();
        UserStatus targetStatus = request.status() != null ? request.status() : user.getStatus();
        boolean removesActiveAdmin = isActiveAdmin(user.getRole().getCode(), user.getStatus())
                && !isActiveAdmin(targetRole.getCode(), targetStatus);
        if (removesActiveAdmin && userRepository.countByRoleCodeAndStatus(ADMIN_ROLE, UserStatus.ACTIVE) <= 1) {
            throw new InvalidStateTransitionException(
                    "LAST_ACTIVE_ADMIN", "마지막 활성 관리자는 강등하거나 비활성화할 수 없습니다."
            );
        }
        if (request.displayName() != null) {
            user.rename(request.displayName());
        }
        user.changeRole(targetRole);
        user.changeStatus(targetStatus);
        return user;
    }

    /** 32단계: 관리자가 비밀번호를 초기화한다. 그 사용자의 기존 토큰은 모두 무효가 된다. */
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        User user = getById(id);
        user.changePassword(passwordEncoder.encode(newPassword), Instant.now());
        // 36단계: 잠긴 사용자(누군가 일부러 틀려 잠근 경우 포함)는 관리자가 비밀번호를 초기화해 바로 풀어 준다
        loginAttemptLimiter.clearUsername(user.getUsername());
    }

    /**
     * 32단계: 본인 비밀번호 변경. 현재 비밀번호가 틀리면 400 {@code CURRENT_PASSWORD_MISMATCH}
     * (401을 쓰면 화면이 로그아웃 처리로 오해한다). 기존 토큰은 모두 무효가 되므로 호출자가 새 토큰을 발급해 돌려준다.
     */
    @Transactional
    public User changeOwnPassword(String username, String currentPassword, String newPassword) {
        User user = getByUsername(username);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidRequestException("CURRENT_PASSWORD_MISMATCH", "현재 비밀번호가 올바르지 않습니다.");
        }
        user.changePassword(passwordEncoder.encode(newPassword), Instant.now());
        return user;
    }

    private Role findRole(String roleCode) {
        return roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new EntityNotFoundException("ROLE_NOT_FOUND", "역할을 찾을 수 없습니다: " + roleCode));
    }

    private static boolean isActiveAdmin(String roleCode, UserStatus status) {
        return ADMIN_ROLE.equals(roleCode) && status == UserStatus.ACTIVE;
    }

    @Transactional
    public UserStationAssignment addStationAssignment(Long userId, Long stationId) {
        User user = getById(userId);
        Station station = stationService.getById(stationId);
        if (userStationAssignmentRepository.existsByUserIdAndStationId(userId, stationId)) {
            throw new DuplicateResourceException(
                    "STATION_ASSIGNMENT_DUPLICATE",
                    "이미 부여된 작업장 권한입니다: userId=" + userId + ", stationId=" + stationId
            );
        }
        return userStationAssignmentRepository.save(new UserStationAssignment(user, station));
    }

    public List<UserStationAssignment> getStationAssignments(Long userId) {
        getById(userId);
        return userStationAssignmentRepository.findByUserId(userId);
    }

    @Transactional
    public void removeStationAssignment(Long userId, Long stationId) {
        getById(userId);
        userStationAssignmentRepository.deleteByUserIdAndStationId(userId, stationId);
    }
}

package com.amrproject.service;

import com.amrproject.domain.entity.Role;
import com.amrproject.domain.entity.Station;
import com.amrproject.domain.entity.User;
import com.amrproject.domain.entity.UserStationAssignment;
import com.amrproject.dto.request.CreateUserRequest;
import com.amrproject.dto.request.UpdateUserRequest;
import com.amrproject.exception.DuplicateResourceException;
import com.amrproject.exception.EntityNotFoundException;
import com.amrproject.repository.RoleRepository;
import com.amrproject.repository.UserRepository;
import com.amrproject.repository.UserStationAssignmentRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserStationAssignmentRepository userStationAssignmentRepository;
    private final StationService stationService;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            UserStationAssignmentRepository userStationAssignmentRepository,
            StationService stationService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.userStationAssignmentRepository = userStationAssignmentRepository;
        this.stationService = stationService;
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

    @Transactional
    public User update(Long id, UpdateUserRequest request) {
        User user = getById(id);
        if (request.displayName() != null) {
            user.rename(request.displayName());
        }
        if (request.status() != null) {
            user.changeStatus(request.status());
        }
        return user;
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

package com.amrproject.controller;

import com.amrproject.dto.request.CreateStationAssignmentRequest;
import com.amrproject.dto.request.CreateUserRequest;
import com.amrproject.dto.request.UpdateUserRequest;
import com.amrproject.dto.response.StationAssignmentResponse;
import com.amrproject.dto.response.UserResponse;
import com.amrproject.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> getAll() {
        return userService.getAll().stream().map(UserResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = UserResponse.from(userService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{userId}")
    public UserResponse getById(@PathVariable Long userId) {
        return UserResponse.from(userService.getById(userId));
    }

    @PatchMapping("/{userId}")
    public UserResponse update(@PathVariable Long userId, @RequestBody UpdateUserRequest request) {
        return UserResponse.from(userService.update(userId, request));
    }

    @GetMapping("/{userId}/station-assignments")
    public List<StationAssignmentResponse> getStationAssignments(@PathVariable Long userId) {
        return userService.getStationAssignments(userId).stream().map(StationAssignmentResponse::from).toList();
    }

    @PostMapping("/{userId}/station-assignments")
    public ResponseEntity<StationAssignmentResponse> addStationAssignment(
            @PathVariable Long userId,
            @Valid @RequestBody CreateStationAssignmentRequest request
    ) {
        StationAssignmentResponse response = StationAssignmentResponse.from(
                userService.addStationAssignment(userId, request.stationId())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{userId}/station-assignments/{stationId}")
    public ResponseEntity<Void> removeStationAssignment(@PathVariable Long userId, @PathVariable Long stationId) {
        userService.removeStationAssignment(userId, stationId);
        return ResponseEntity.noContent().build();
    }
}

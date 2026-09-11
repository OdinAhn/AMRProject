package com.amrproject.dto.response;

import com.amrproject.domain.entity.User;
import com.amrproject.domain.entity.UserStatus;

import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String displayName,
        String roleCode,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole().getCode(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}

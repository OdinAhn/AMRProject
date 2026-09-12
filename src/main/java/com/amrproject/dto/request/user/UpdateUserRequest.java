package com.amrproject.dto.request.user;

import com.amrproject.domain.entity.user.UserStatus;

public record UpdateUserRequest(
        String displayName,
        UserStatus status
) {
}

package com.amrproject.dto.request;

import com.amrproject.domain.entity.UserStatus;

public record UpdateUserRequest(
        String displayName,
        UserStatus status
) {
}

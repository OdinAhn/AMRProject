package com.amrproject.dto.request.user;

import com.amrproject.domain.entity.user.UserStatus;

public record UpdateUserRequest(
        String displayName,
        UserStatus status,
        /** 32단계: 역할 변경(ADMIN/WORKER). null이면 그대로 */
        String roleCode
) {
}

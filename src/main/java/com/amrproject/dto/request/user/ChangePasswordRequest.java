package com.amrproject.dto.request.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 32단계: 로그인한 사용자가 자기 비밀번호를 바꾼다. 현재 비밀번호를 확인한다. */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8) String newPassword
) {
}

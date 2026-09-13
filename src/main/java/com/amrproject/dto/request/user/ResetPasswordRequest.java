package com.amrproject.dto.request.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 32단계: 관리자가 다른 사용자의 비밀번호를 초기화한다. 규칙은 사용자 생성(CreateUserRequest)과 같다(8자 이상). */
public record ResetPasswordRequest(
        @NotBlank @Size(min = 8) String newPassword
) {
}

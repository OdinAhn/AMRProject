package com.amrproject.dto.request.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String displayName,
        @NotBlank String roleCode
) {
}

package com.amrproject.dto.response.user;

public record LoginResponse(
        String token,
        UserResponse user
) {
}

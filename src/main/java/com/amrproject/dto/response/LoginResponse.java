package com.amrproject.dto.response;

public record LoginResponse(
        String token,
        UserResponse user
) {
}

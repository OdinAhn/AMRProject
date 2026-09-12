package com.amrproject.controller;

import com.amrproject.dto.request.user.LoginRequest;
import com.amrproject.dto.response.user.LoginResponse;
import com.amrproject.dto.response.user.UserResponse;
import com.amrproject.service.AuthService;
import com.amrproject.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API (docs/API.md §1, 10단계). 자체 서명 토큰(AccessTokenService) 기반이라
 * 서버가 들고 있는 세션 상태가 없다 — logout은 클라이언트가 토큰을 버리는 것으로
 * 처리하고, 서버 측 토큰 무효화(블랙리스트)는 구현하지 않는다.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.username(), request.password());
        return new LoginResponse(result.token(), UserResponse.from(result.user()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return UserResponse.from(userService.getByUsername(authentication.getName()));
    }
}

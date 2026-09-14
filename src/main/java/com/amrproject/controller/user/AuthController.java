package com.amrproject.controller.user;

import com.amrproject.dto.request.user.ChangePasswordRequest;
import com.amrproject.dto.request.user.LoginRequest;
import com.amrproject.dto.response.user.LoginResponse;
import com.amrproject.dto.response.user.UserResponse;
import com.amrproject.service.user.AuthService;
import com.amrproject.service.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        // 36단계: prod 프로필은 리버스 프록시의 X-Forwarded-For로 실제 클라이언트 주소를 채운다(docs/DEPLOYMENT.md)
        AuthService.LoginResult result = authService.login(request.username(), request.password(), httpRequest.getRemoteAddr());
        return new LoginResponse(result.token(), UserResponse.from(result.user()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }

    /**
     * 32단계: 본인 비밀번호 변경. 기존 토큰(다른 기기 포함)은 모두 무효가 되므로, 지금 쓰던 화면이 로그인 상태를 유지하도록
     * 새 토큰을 발급해 로그인 응답과 같은 모양으로 돌려준다.
     */
    @PutMapping("/password")
    public LoginResponse changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
        var user = userService.changeOwnPassword(authentication.getName(), request.currentPassword(), request.newPassword());
        return new LoginResponse(authService.issueToken(user), UserResponse.from(user));
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return UserResponse.from(userService.getByUsername(authentication.getName()));
    }
}

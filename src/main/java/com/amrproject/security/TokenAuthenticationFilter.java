package com.amrproject.security;

import com.amrproject.domain.entity.user.User;
import com.amrproject.domain.entity.user.UserStatus;
import com.amrproject.repository.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Authorization: Bearer 헤더의 접근 토큰을 검증해 SecurityContext를 채운다 (10단계).
 * 토큰이 없거나 유효하지 않아도 그냥 다음 필터로 넘긴다 — 인증 필요 여부와 실패 시
 * 401/403 응답은 SecurityConfig의 authorizeHttpRequests/authenticationEntryPoint가 결정한다.
 *
 * <p>19단계: 브라우저 네이티브 {@code EventSource}는 커스텀 헤더를 보낼 수 없어
 * {@link #SSE_STREAM_PATH} 요청에 한해서만 {@code access_token} 쿼리 파라미터를 대체
 * 토큰 소스로 허용한다 — 다른 모든 엔드포인트는 계속 헤더만 받는다. 토큰이 URL에
 * 노출되는 트레이드오프는 docs/API.md §5에 명시했다.
 *
 * <p>26단계: 서명·만료만 보지 않고 매 요청마다 토큰의 사용자를 DB에서 다시 조회한다 —
 * 비활성화된 사용자는 토큰이 아직 만료 전이어도 인증되지 않고(401), 권한은 토큰에
 * 적힌 role이 아니라 DB의 현재 role로 부여한다(역할 변경이 즉시 반영됨).
 *
 * <p>32단계: 비밀번호 변경 시각보다 먼저 발급된 토큰도 인증하지 않는다.
 */
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SSE_STREAM_PATH = "/api/events/stream";
    private static final String EXTERNAL_PATH_PREFIX = "/api/external/";

    private final AccessTokenService accessTokenService;
    private final UserRepository userRepository;

    public TokenAuthenticationFilter(AccessTokenService accessTokenService, UserRepository userRepository) {
        this.accessTokenService = accessTokenService;
        this.userRepository = userRepository;
    }

    /** 외부 시스템 API는 사용자 토큰이 아니라 API 키로만 인증한다 (26단계, ExternalApiKeyFilter). */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(EXTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            accessTokenService.parseToken(token)
                    .flatMap(payload -> findActiveUser(payload.userId())
                            .filter(user -> issuedAfterPasswordChange(payload, user)))
                    .ifPresent(user -> {
                        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().getCode()));
                        var authentication = new UsernamePasswordAuthenticationToken(user.getUsername(), null, authorities);
                        // 37단계: 감사 로그가 행위자 id를 추가 조회 없이 쓰도록 사용자 id를 담아 둔다(AuditLogInterceptor)
                        authentication.setDetails(user.getId());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 32단계: 비밀번호를 바꾸면 그 전에 발급된 토큰은 모두 무효다(도난당한 토큰을 비밀번호 변경으로 끊을 수 있게).
     * 초 단위로 비교해, 변경 직후 같은 초에 새로 발급한 토큰은 유효하다.
     */
    private static boolean issuedAfterPasswordChange(AccessTokenService.TokenPayload payload, User user) {
        return user.getPasswordChangedAt() == null
                || payload.issuedAtEpochSecond() >= user.getPasswordChangedAt().getEpochSecond();
    }

    private Optional<User> findActiveUser(Long userId) {
        return userRepository.findWithRoleById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        if (SSE_STREAM_PATH.equals(request.getRequestURI())) {
            return request.getParameter("access_token");
        }
        return null;
    }
}

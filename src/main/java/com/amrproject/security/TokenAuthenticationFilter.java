package com.amrproject.security;

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
 */
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SSE_STREAM_PATH = "/api/events/stream";

    private final AccessTokenService accessTokenService;

    public TokenAuthenticationFilter(AccessTokenService accessTokenService) {
        this.accessTokenService = accessTokenService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            Optional<AccessTokenService.TokenPayload> payload = accessTokenService.parseToken(token);
            payload.ifPresent(p -> {
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + p.role()));
                var authentication = new UsernamePasswordAuthenticationToken(p.username(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        filterChain.doFilter(request, response);
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

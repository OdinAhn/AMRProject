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
 */
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

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
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            Optional<AccessTokenService.TokenPayload> payload = accessTokenService.parseToken(token);
            payload.ifPresent(p -> {
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + p.role()));
                var authentication = new UsernamePasswordAuthenticationToken(p.username(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        filterChain.doFilter(request, response);
    }
}

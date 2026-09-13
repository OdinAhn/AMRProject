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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 26단계: 외부 시스템(WMS/WCS) API({@code /api/external/**}) 인증. 9단계부터 이 경로는
 * 인증 방식 미정으로 permitAll이라 누구나 작업을 만들 수 있었다. 실제 연동 대상이 정해지기
 * 전에도 성립하는 최소한의 방식으로 공유 API 키({@code X-API-Key} 헤더)를 검증해
 * {@code ROLE_EXTERNAL}을 부여한다. 설정된 키가 비어 있으면 어떤 요청도 인증하지 않는다
 * (fail-closed) — 운영에서 키 설정을 빠뜨려도 API가 열리지 않는다.
 */
public class ExternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    private static final String EXTERNAL_PATH_PREFIX = "/api/external/";

    private final byte[] expectedKey;

    public ExternalApiKeyFilter(String apiKey) {
        this.expectedKey = apiKey == null ? new byte[0] : apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(EXTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String providedKey = request.getHeader(HEADER);
        if (expectedKey.length > 0 && providedKey != null
                && MessageDigest.isEqual(expectedKey, providedKey.getBytes(StandardCharsets.UTF_8))) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    "external-system", null, List.of(new SimpleGrantedAuthority("ROLE_EXTERNAL"))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }
}

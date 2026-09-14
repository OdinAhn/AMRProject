package com.amrproject.config;

import com.amrproject.repository.user.UserRepository;
import com.amrproject.security.AccessTokenService;
import com.amrproject.security.ExternalApiKeyFilter;
import com.amrproject.security.TokenAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 10단계: 자체 서명 접근 토큰(AccessTokenService) 기반 인증·권한.
 * 26단계: /api/external/**은 더 이상 permitAll이 아니다 — 공유 API 키(ExternalApiKeyFilter)로
 * ROLE_EXTERNAL을 받아야 호출할 수 있다 (docs/API.md §6).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            AccessTokenService accessTokenService,
            UserRepository userRepository,
            @Value("${app.external.api-key:}") String externalApiKey,
            @Value("${management.server.port:-1}") int managementPort
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) -> response.sendError(401, "Unauthorized")
                ))
                .authorizeHttpRequests(auth -> auth
                        // SSE(비동기 응답)를 마무리하는 ASYNC dispatch는 이미 인가를 통과한 원래 요청의 연장이라 다시 검사하지 않는다.
                        // 토큰 필터(OncePerRequestFilter)는 ASYNC dispatch에서 돌지 않아, 검사하면 인증 정보가 없어 거부되고
                        // 이미 응답을 보낸 뒤라 "response is already committed" ERROR 로그만 남았다(클라이언트가 끊은 SSE에 이벤트를 보낼 때).
                        // ASYNC dispatch는 서블릿 컨테이너가 내부에서만 만들며 클라이언트가 직접 요청할 수 없다.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        // 35단계: 운영 프로필은 actuator를 별도 관리 포트(기본 8081)로 띄우고 외부에 공개하지 않는다 —
                        // 그 포트로 들어온 요청(Prometheus 수집 등)만 토큰 없이 허용한다.
                        .requestMatchers(request -> managementPort > 0 && request.getLocalPort() == managementPort).permitAll()
                        .requestMatchers(
                                "/actuator/health/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api/auth/login"
                        ).permitAll()
                        // 35단계: 애플리케이션 포트로 노출된 나머지 actuator(메트릭 등)는 관리자만
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/api/external/**").hasRole("EXTERNAL")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/worker/**").hasAnyRole("ADMIN", "WORKER")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new TokenAuthenticationFilter(accessTokenService, userRepository), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new ExternalApiKeyFilter(externalApiKey), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

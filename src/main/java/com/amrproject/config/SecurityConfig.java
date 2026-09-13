package com.amrproject.config;

import com.amrproject.repository.user.UserRepository;
import com.amrproject.security.AccessTokenService;
import com.amrproject.security.ExternalApiKeyFilter;
import com.amrproject.security.TokenAuthenticationFilter;
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
            @Value("${app.external.api-key:}") String externalApiKey
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) -> response.sendError(401, "Unauthorized")
                ))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api/auth/login"
                        ).permitAll()
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

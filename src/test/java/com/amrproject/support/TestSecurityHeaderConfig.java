package com.amrproject.support;

import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 10단계: 기존 테스트를 한 줄도 안 건드리기 위한 장치. @AutoConfigureMockMvc가
 * 자동으로 집어가는 MockMvcBuilderCustomizer 빈을 등록해, 실제로 시드한 관리자
 * 계정의 유효한 토큰을 모든 mockMvc.perform(...) 요청에 기본 헤더로 붙인다 —
 * 보안을 끄는 게 아니라 모든 테스트가 진짜로 인증된 관리자로 요청하게 만든다.
 * 401/403 같은 미인증 시나리오는 이 기본 헤더를 쓰지 않는 AuthenticationTest에서 검증한다.
 */
@Configuration
public class TestSecurityHeaderConfig {

    @Bean
    public MockMvcBuilderCustomizer defaultAdminAuthorizationHeader(TestAdminTokenProvider tokenProvider) {
        String token = tokenProvider.issueAdminToken();
        return builder -> builder.defaultRequest(
                MockMvcRequestBuilders.get("/").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        );
    }
}

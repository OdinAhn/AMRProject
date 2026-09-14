package com.amrproject.controller;

import com.amrproject.domain.entity.user.Role;
import com.amrproject.domain.entity.user.User;
import com.amrproject.dto.request.user.LoginRequest;
import com.amrproject.dto.request.user.ResetPasswordRequest;
import com.amrproject.repository.user.RoleRepository;
import com.amrproject.repository.user.UserRepository;
import com.amrproject.support.AbstractIntegrationTest;
import com.amrproject.support.TestAdminTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 36단계: 로그인 실패 횟수 제한(아이디 5회·IP 50회, 15분). 카운터는 테스트 컨테이너의 Redis에 남으므로 테스트마다 아이디와
 * 접속 IP를 따로 쓴다 — 다른 테스트(MockMvc 기본 127.0.0.1)의 IP 카운터와도 섞이지 않는다.
 * 기본 관리자 헤더가 붙지 않도록 MockMvc를 직접 만든다(AuthenticationTest와 같은 이유).
 */
class LoginRateLimitTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "correct-password";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TestAdminTokenProvider testAdminTokenProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void 아이디별로_5번_틀리면_맞는_비밀번호도_429로_막고_다른_아이디는_영향이_없다() throws Exception {
        createUser("limit-user-1");
        createUser("limit-user-2");
        String ip = "10.36.0.1";

        for (int i = 0; i < 5; i++) {
            login("limit-user-1", "wrong-password", ip).andExpect(status().isUnauthorized());
        }

        String retryAfter = login("limit-user-1", PASSWORD, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"))
                .andReturn().getResponse().getHeader("Retry-After");
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 900L);

        // 대소문자만 다른 아이디도 같은 계정이라 같이 막힌다(MySQL 기본 콜레이션)
        login("LIMIT-USER-1", PASSWORD, ip).andExpect(status().isTooManyRequests());
        // 다른 IP에서도 막힌다 — 아이디 기준 잠금
        login("limit-user-1", PASSWORD, "10.36.0.2").andExpect(status().isTooManyRequests());
        // 같은 IP의 다른 아이디는 로그인된다
        login("limit-user-2", PASSWORD, ip).andExpect(status().isOk());
    }

    @Test
    void 없는_아이디도_똑같이_막혀_아이디_존재_여부가_드러나지_않는다() throws Exception {
        String ip = "10.36.1.1";
        for (int i = 0; i < 5; i++) {
            login("no-such-user-36", "whatever", ip).andExpect(status().isUnauthorized());
        }
        login("no-such-user-36", "whatever", ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
    }

    @Test
    void 로그인에_성공하면_아이디_실패_횟수가_초기화된다() throws Exception {
        createUser("limit-user-3");
        String ip = "10.36.2.1";

        for (int i = 0; i < 4; i++) {
            login("limit-user-3", "wrong-password", ip).andExpect(status().isUnauthorized());
        }
        login("limit-user-3", PASSWORD, ip).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            login("limit-user-3", "wrong-password", ip).andExpect(status().isUnauthorized());
        }
        // 누적이었다면 8번째 실패 뒤라 막혔을 것이다
        login("limit-user-3", PASSWORD, ip).andExpect(status().isOk());
    }

    @Test
    void 한_IP에서_여러_아이디로_50번_틀리면_그_IP는_막히고_다른_IP는_된다() throws Exception {
        createUser("limit-user-4");
        String ip = "10.36.3.1";

        for (int i = 0; i < 50; i++) {
            login("spray-" + i, "guess", ip).andExpect(status().isUnauthorized());
        }
        login("limit-user-4", PASSWORD, ip).andExpect(status().isTooManyRequests());
        login("limit-user-4", PASSWORD, "10.36.3.2").andExpect(status().isOk());
    }

    @Test
    void 관리자가_비밀번호를_초기화하면_잠금이_바로_풀린다() throws Exception {
        User user = createUser("limit-user-5");
        String ip = "10.36.4.1";
        for (int i = 0; i < 5; i++) {
            login("limit-user-5", "wrong-password", ip).andExpect(status().isUnauthorized());
        }
        login("limit-user-5", PASSWORD, ip).andExpect(status().isTooManyRequests());

        mockMvc.perform(put("/api/admin/users/{userId}/password", user.getId())
                        .header("Authorization", "Bearer " + testAdminTokenProvider.issueAdminToken())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest("new-password-36"))))
                .andExpect(status().isNoContent());

        login("limit-user-5", "new-password-36", ip).andExpect(status().isOk());
    }

    private ResultActions login(String username, String password, String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, password))));
    }

    private User createUser(String username) {
        Role role = roleRepository.findByCode("WORKER").orElseThrow();
        return userRepository.save(new User(username, passwordEncoder.encode(PASSWORD), username, role));
    }
}

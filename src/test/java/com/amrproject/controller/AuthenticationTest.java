package com.amrproject.controller;

import com.amrproject.domain.entity.user.Role;
import com.amrproject.domain.entity.user.User;
import com.amrproject.domain.entity.user.UserStatus;
import com.amrproject.dto.request.user.LoginRequest;
import com.amrproject.repository.user.RoleRepository;
import com.amrproject.repository.user.UserRepository;
import com.amrproject.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 10단계 완료 기준(관리자 설정과 작업자 권한이 검증된다)을 확인한다.
 * 다른 테스트와 달리 @AutoConfigureMockMvc를 쓰지 않고 MockMvc를 직접 만든다 —
 * TestSecurityHeaderConfig의 기본 관리자 토큰 헤더가 자동으로 붙으면 401/403
 * 시나리오를 검증할 수 없기 때문이다.
 */
class AuthenticationTest extends AbstractIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void 올바른_자격증명으로_로그인하면_토큰과_사용자정보를_반환한다() throws Exception {
        createUser("auth-admin-1", "correct-password", "ADMIN");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("auth-admin-1", "correct-password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("auth-admin-1"))
                .andExpect(jsonPath("$.user.roleCode").value("ADMIN"));
    }

    @Test
    void 잘못된_비밀번호나_존재하지_않는_사용자는_401을_반환한다() throws Exception {
        createUser("auth-admin-2", "correct-password", "ADMIN");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("auth-admin-2", "wrong-password"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("no-such-user", "whatever"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 토큰_없이_관리자_API를_호출하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/robots"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void WORKER_토큰으로_관리자_API를_호출하면_403을_반환한다() throws Exception {
        createUser("auth-worker-1", "worker-password", "WORKER");
        String token = login("auth-worker-1", "worker-password");

        mockMvc.perform(get("/api/admin/robots").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void ADMIN_토큰으로_관리자_API를_호출하면_성공하고_me는_현재_사용자를_반환한다() throws Exception {
        createUser("auth-admin-3", "admin-password", "ADMIN");
        String token = login("auth-admin-3", "admin-password");

        mockMvc.perform(get("/api/admin/robots").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("auth-admin-3"))
                .andExpect(jsonPath("$.roleCode").value("ADMIN"));
    }

    @Test
    void 비활성화된_사용자의_기존_토큰은_만료_전이어도_401을_반환한다() throws Exception {
        createUser("auth-admin-4", "admin-password", "ADMIN");
        String token = login("auth-admin-4", "admin-password");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        User user = userRepository.findByUsername("auth-admin-4").orElseThrow();
        user.changeStatus(UserStatus.DISABLED);
        userRepository.save(user);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 역할이_바뀌면_기존_토큰의_권한도_DB의_현재_역할을_따른다() throws Exception {
        createUser("auth-admin-5", "admin-password", "ADMIN");
        String token = login("auth-admin-5", "admin-password");
        mockMvc.perform(get("/api/admin/robots").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 역할 변경 API는 아직 없어(UpdateUserRequest는 displayName/status만) DB를 직접 바꿔 검증한다.
        jdbcTemplate.update(
                "update users set role_id = ? where username = ?",
                roleRepository.findByCode("WORKER").orElseThrow().getId(), "auth-admin-5"
        );

        mockMvc.perform(get("/api/admin/robots").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asString();
    }

    private void createUser(String username, String password, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        userRepository.save(new User(username, passwordEncoder.encode(password), username, role));
    }
}

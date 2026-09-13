package com.amrproject.controller;

import com.amrproject.domain.entity.user.Role;
import com.amrproject.domain.entity.user.User;
import com.amrproject.dto.request.user.ChangePasswordRequest;
import com.amrproject.dto.request.user.LoginRequest;
import com.amrproject.dto.request.user.ResetPasswordRequest;
import com.amrproject.dto.request.user.UpdateUserRequest;
import com.amrproject.repository.user.RoleRepository;
import com.amrproject.repository.user.UserRepository;
import com.amrproject.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 32단계: 역할 변경, 관리자 비밀번호 초기화, 본인 비밀번호 변경과 그에 따른 기존 토큰 무효화를 검증한다.
 * 사용자별 토큰으로 요청해야 해서 AuthenticationTest처럼 MockMvc를 직접 만든다(기본 관리자 헤더 없음).
 */
class AccountManagementTest extends AbstractIntegrationTest {

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

    private MockMvc mockMvc;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        ensureUser("acct-admin", "admin-password", "ADMIN");
        adminToken = login("acct-admin", "admin-password");
    }

    @Test
    void 관리자가_작업자를_관리자로_바꾸면_기존_토큰으로도_관리자_API를_쓸_수_있다() throws Exception {
        Long userId = ensureUser("acct-worker-1", "worker-password", "WORKER");
        String workerToken = login("acct-worker-1", "worker-password");
        mockMvc.perform(get("/api/admin/robots").header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/admin/users/{userId}", userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRequest(null, null, "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleCode").value("ADMIN"));

        mockMvc.perform(get("/api/admin/robots").header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isOk());
    }

    @Test
    void 관리자가_비밀번호를_초기화하면_기존_토큰은_401이고_새_비밀번호로_로그인된다() throws Exception {
        Long userId = ensureUser("acct-worker-2", "old-password", "WORKER");
        String oldToken = login("acct-worker-2", "old-password");
        waitForNextSecond();

        mockMvc.perform(put("/api/admin/users/{userId}/password", userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest("new-password-1"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("acct-worker-2", "old-password"))))
                .andExpect(status().isUnauthorized());
        login("acct-worker-2", "new-password-1");

        mockMvc.perform(put("/api/admin/users/{userId}/password", userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest("short"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 본인_비밀번호_변경은_현재_비밀번호를_확인하고_새_토큰을_돌려주며_기존_토큰은_무효가_된다() throws Exception {
        ensureUser("acct-worker-3", "current-password", "WORKER");
        String oldToken = login("acct-worker-3", "current-password");
        waitForNextSecond();

        mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", "Bearer " + oldToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest("wrong-password", "changed-password"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"));

        String response = mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", "Bearer " + oldToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest("current-password", "changed-password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("acct-worker-3"))
                .andReturn().getResponse().getContentAsString();
        String newToken = objectMapper.readTree(response).get("token").asString();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
    }

    /** 토큰 발급 시각(iat)은 초 단위라, 같은 초 안에 발급·변경하면 기존 토큰이 유효하게 남는다 — 다음 초로 넘긴다. */
    private static void waitForNextSecond() throws InterruptedException {
        Thread.sleep(1100);
    }

    private Long ensureUser(String username, String password, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        return userRepository.findByUsername(username)
                .orElseGet(() -> userRepository.save(new User(username, passwordEncoder.encode(password), username, role)))
                .getId();
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asString();
    }
}

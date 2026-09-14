package com.amrproject.controller;

import com.amrproject.domain.entity.event.AuditLog;
import com.amrproject.domain.entity.site.StationType;
import com.amrproject.domain.entity.user.Role;
import com.amrproject.domain.entity.user.User;
import com.amrproject.dto.request.robot.CreateRobotRequest;
import com.amrproject.dto.request.site.CreateSiteRequest;
import com.amrproject.dto.request.site.CreateStationRequest;
import com.amrproject.dto.request.task.ChangeTaskPriorityRequest;
import com.amrproject.dto.request.task.CreateTaskRequest;
import com.amrproject.dto.request.user.LoginRequest;
import com.amrproject.dto.request.user.ResetPasswordRequest;
import com.amrproject.repository.event.AuditLogRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 37단계: 감사 로그 — 상태를 바꾸는 API 호출(성공·실패)과 로그인 결과가 행위자·대상·결과와 함께 남고, 조회(GET)와 요청 본문(비밀번호)은
 * 남지 않으며, 관리자만 조회할 수 있다.
 *
 * <p>관리자·작업자 토큰을 명시적으로 쓰도록 MockMvc를 직접 만든다. 다른 테스트도 같은 DB에 감사 로그를 쌓으므로 이 테스트가 만든
 * 대상 id·아이디로 걸러서 확인한다.
 */
class AuditLogTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private TestAdminTokenProvider testAdminTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private String adminToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        adminToken = testAdminTokenProvider.issueAdminToken();
    }

    @Test
    void 상태를_바꾸는_호출은_성공과_실패_모두_행위자_대상_결과와_함께_남고_조회는_남지_않는다() throws Exception {
        Long siteId = createdId(post("/api/admin/sites"), new CreateSiteRequest("SITE-AUD1", "감사센터"));
        Long originId = createdId(post("/api/admin/sites/{siteId}/stations", siteId), new CreateStationRequest("ST-AUD1", "입고", StationType.PICKUP));
        Long destinationId = createdId(post("/api/admin/sites/{siteId}/stations", siteId), new CreateStationRequest("ST-AUD2", "출고", StationType.DROPOFF));
        Long robotId = createdId(post("/api/admin/robots"), new CreateRobotRequest("ROBOT-AUD1", "감사로봇", null, null, siteId));

        String taskResponse = mockMvc.perform(post("/api/worker/tasks").header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "idem-audit-1")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTaskRequest(originId, destinationId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long taskId = objectMapper.readTree(taskResponse).get("id").asLong();

        mockMvc.perform(post("/api/admin/tasks/{taskId}/cancel", taskId).header("Authorization", "Bearer " + adminToken)
                        .with(request -> {
                            request.setRemoteAddr("10.37.0.1");
                            return request;
                        }))
                .andExpect(status().isOk());
        // 취소된 작업의 우선순위 변경 → 409도 남는다
        mockMvc.perform(patch("/api/admin/tasks/{taskId}/priority", taskId).header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeTaskPriorityRequest(5))))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/admin/tasks/{taskId}", taskId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        List<AuditLog> taskLogs = logsFor("TASK", taskId);
        assertThat(taskLogs).extracting(AuditLog::getAction)
                .containsExactly("POST /api/admin/tasks/{taskId}/cancel", "PATCH /api/admin/tasks/{taskId}/priority");
        AuditLog cancel = taskLogs.get(0);
        assertThat(cancel.getActorUsername()).isEqualTo("test-admin");
        assertThat(cancel.getActorUserId()).isNotNull();
        assertThat(cancel.getHttpStatus()).isEqualTo(200);
        assertThat(cancel.getIpAddress()).isEqualTo("10.37.0.1");
        assertThat(cancel.getPayloadJson()).contains("\"taskId\":\"" + taskId + "\"");
        assertThat(taskLogs.get(1).getHttpStatus()).isEqualTo(409);

        // 경로에 대상이 없는 생성 API는 만든 리소스 id를 남긴다
        assertThat(auditLogRepository.findAll()).anyMatch(log -> "POST /api/admin/robots".equals(log.getAction())
                && log.getHttpStatus() == 201 && log.getPayloadJson().contains("\"createdId\":" + robotId));
        // 하위 리소스 생성은 경로에 있는 상위 리소스가 대상이다(작업장 생성 → 물류센터)
        assertThat(logsFor("SITE", siteId)).extracting(AuditLog::getAction)
                .containsOnly("POST /api/admin/sites/{siteId}/stations");
    }

    @Test
    void 로그인_결과는_사유와_함께_남고_비밀번호는_어디에도_남지_않는다() throws Exception {
        Role workerRole = roleRepository.findByCode("WORKER").orElseThrow();
        User user = userRepository.save(new User("audit-user-1", passwordEncoder.encode("first-password"), "감사사용자", workerRole));

        login("audit-user-1", "wrong-password-37").andExpect(status().isUnauthorized());
        login("audit-nobody-37", "whatever-37").andExpect(status().isUnauthorized());
        login("audit-user-1", "first-password").andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/users/{userId}/password", user.getId()).header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest("secret-new-password-37"))))
                .andExpect(status().isNoContent());

        List<AuditLog> userLogs = logsFor("USER", user.getId());
        assertThat(userLogs).extracting(AuditLog::getAction)
                .containsExactly("AUTH_LOGIN_FAILED", "AUTH_LOGIN_SUCCEEDED", "PUT /api/admin/users/{userId}/password");
        assertThat(userLogs.get(0).getPayloadJson()).contains("WRONG_PASSWORD");
        assertThat(userLogs.get(1).getActorUserId()).isEqualTo(user.getId());

        AuditLog unknown = auditLogRepository.findAll().stream()
                .filter(log -> "audit-nobody-37".equals(log.getActorUsername())).findFirst().orElseThrow();
        assertThat(unknown.getAction()).isEqualTo("AUTH_LOGIN_FAILED");
        assertThat(unknown.getActorUserId()).isNull();
        assertThat(unknown.getPayloadJson()).contains("UNKNOWN_USERNAME");

        assertThat(auditLogRepository.findAll()).noneMatch(log -> log.getPayloadJson() != null
                && (log.getPayloadJson().contains("secret-new-password-37") || log.getPayloadJson().contains("wrong-password-37")
                || log.getPayloadJson().contains("first-password")));
    }

    @Test
    void 감사_로그는_관리자만_필터로_조회한다() throws Exception {
        Long siteId = createdId(post("/api/admin/sites"), new CreateSiteRequest("SITE-AUD2", "감사센터2"));
        mockMvc.perform(patch("/api/admin/sites/{siteId}", siteId).header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"감사센터2-수정\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", "Bearer " + adminToken)
                        .param("targetType", "SITE").param("targetId", String.valueOf(siteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("PATCH /api/admin/sites/{siteId}"))
                .andExpect(jsonPath("$.content[0].actorUsername").value("test-admin"))
                .andExpect(jsonPath("$.content[0].httpStatus").value(200));

        Role workerRole = roleRepository.findByCode("WORKER").orElseThrow();
        userRepository.save(new User("audit-worker-1", passwordEncoder.encode("worker-password"), "감사작업자", workerRole));
        String workerToken = objectMapper.readTree(login("audit-worker-1", "worker-password").andReturn().getResponse().getContentAsString())
                .get("token").asString();
        mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isForbidden());
    }

    private List<AuditLog> logsFor(String targetType, Long targetId) {
        return auditLogRepository.findAll().stream()
                .filter(log -> targetType.equals(log.getTargetType()) && targetId.equals(log.getTargetId()))
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .toList();
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr("10.37.1.1");
                    return request;
                })
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, password))));
    }

    private Long createdId(MockHttpServletRequestBuilder request, Object body) throws Exception {
        String response = mockMvc.perform(request.header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}

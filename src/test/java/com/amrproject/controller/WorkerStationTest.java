package com.amrproject.controller;

import com.amrproject.domain.entity.user.Role;
import com.amrproject.domain.entity.site.StationType;
import com.amrproject.domain.entity.user.User;
import com.amrproject.dto.request.site.CreateSiteRequest;
import com.amrproject.dto.request.user.CreateStationAssignmentRequest;
import com.amrproject.dto.request.site.CreateStationRequest;
import com.amrproject.dto.request.task.CreateTaskRequest;
import com.amrproject.dto.request.user.CreateUserRequest;
import com.amrproject.dto.request.user.LoginRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 12단계 완료 기준(작업장 업무를 화면으로 완료)에 필요한 작업자 조회/취소
 * API를 검증한다. 특정 WORKER 계정으로 로그인해야 해서 (10단계의 기본 관리자
 * 헤더가 아니라) AuthenticationTest와 같은 방식으로 MockMvc를 직접 만든다.
 */
class WorkerStationTest extends AbstractIntegrationTest {

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
        Role adminRole = roleRepository.findByCode("ADMIN").orElseThrow();
        userRepository.findByUsername("ws-admin")
                .orElseGet(() -> userRepository.save(new User("ws-admin", passwordEncoder.encode("admin-password"), "관리자", adminRole)));
        adminToken = login("ws-admin", "admin-password");
    }

    @Test
    void 작업자는_자기_배정_작업장만_목록에서_본다() throws Exception {
        Long siteId = createSite("SITE-WS1", "작업자물류센터1");
        Long myStationId = createStation(siteId, "ST-WS1", "내작업장");
        createStation(siteId, "ST-WS2", "남의작업장");

        String workerToken = createWorker("worker-ws-1", siteId, myStationId);

        String response = mockMvc.perform(get("/api/worker/stations").header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var stations = objectMapper.readTree(response);
        assertThat(stations.size()).isEqualTo(1);
        assertThat(stations.get(0).get("code").asString()).isEqualTo("ST-WS1");
    }

    @Test
    void 작업장별_작업목록은_종료된_작업을_제외한다() throws Exception {
        Long siteId = createSite("SITE-WS2", "작업자물류센터2");
        Long originId = createStation(siteId, "ST-WS3", "입고");
        Long destinationId = createStation(siteId, "ST-WS4", "출고");
        String workerToken = createWorker("worker-ws-2", siteId, originId);

        Long taskId = createTask(originId, destinationId, "idem-ws-1", workerToken);

        String response = mockMvc.perform(get("/api/worker/stations/{stationId}/tasks", originId)
                        .header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var tasks = objectMapper.readTree(response);
        assertThat(tasks.size()).isEqualTo(1);
        assertThat(tasks.get(0).get("id").asLong()).isEqualTo(taskId);

        mockMvc.perform(post("/api/worker/tasks/{taskId}/cancel", taskId).header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        String afterCancel = mockMvc.perform(get("/api/worker/stations/{stationId}/tasks", originId)
                        .header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(afterCancel).size()).isEqualTo(0);
    }

    @Test
    void 작업자는_같은_site의_다른_작업장_목록을_목적지_선택용으로_조회할_수_있다() throws Exception {
        Long siteId = createSite("SITE-WS3", "작업자물류센터3");
        Long originId = createStation(siteId, "ST-WS5", "입고");
        createStation(siteId, "ST-WS6", "출고");
        String workerToken = createWorker("worker-ws-3", siteId, originId);

        mockMvc.perform(get("/api/worker/sites/{siteId}/stations", siteId).header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    private String createWorker(String username, Long siteId, Long stationId) throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateUserRequest(username, "worker-password", username, "WORKER"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long userId = userRepository.findByUsername(username).orElseThrow().getId();
        mockMvc.perform(post("/api/admin/users/{userId}/station-assignments", userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateStationAssignmentRequest(stationId))))
                .andExpect(status().isCreated());
        return login(username, "worker-password");
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asString();
    }

    private Long createTask(Long originId, Long destinationId, String idempotencyKey, String token) throws Exception {
        String response = mockMvc.perform(post("/api/worker/tasks")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTaskRequest(originId, destinationId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private Long createSite(String code, String name) throws Exception {
        String response = mockMvc.perform(post("/api/admin/sites")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSiteRequest(code, name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private Long createStation(Long siteId, String code, String name) throws Exception {
        String response = mockMvc.perform(post("/api/admin/sites/{siteId}/stations", siteId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateStationRequest(code, name, StationType.GENERIC))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}

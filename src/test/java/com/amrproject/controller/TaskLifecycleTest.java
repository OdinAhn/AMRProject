package com.amrproject.controller;

import com.amrproject.domain.entity.Robot;
import com.amrproject.domain.entity.StationType;
import com.amrproject.dto.request.CreateRobotRequest;
import com.amrproject.dto.request.CreateSiteRequest;
import com.amrproject.dto.request.CreateStationRequest;
import com.amrproject.dto.request.CreateTaskRequest;
import com.amrproject.repository.RobotRepository;
import com.amrproject.service.TaskService;
import com.amrproject.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 3단계 완료 기준(중복 요청과 잘못된 전이를 차단)을 REST 레벨에서 검증한다.
 * 배정→진행→완료 경로는 아직 REST로 노출되지 않아 TaskService를 직접 호출한다.
 */
@AutoConfigureMockMvc
class TaskLifecycleTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private TaskService taskService;

    @Autowired
    private RobotRepository robotRepository;

    @Test
    void 동일한_Idempotency_Key로_두번_호출하면_같은_작업을_반환한다() throws Exception {
        Long siteId = createSite("SITE-A", "물류센터A");
        Long originId = createStation(siteId, "ST-A1", "입고A", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-A2", "출고A", StationType.DROPOFF);
        String body = objectMapper.writeValueAsString(new CreateTaskRequest(originId, destinationId));

        String firstResponse = mockMvc.perform(post("/api/worker/tasks")
                        .header("Idempotency-Key", "idem-key-1")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long firstTaskId = objectMapper.readTree(firstResponse).get("id").asLong();

        String secondResponse = mockMvc.perform(post("/api/worker/tasks")
                        .header("Idempotency-Key", "idem-key-1")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long secondTaskId = objectMapper.readTree(secondResponse).get("id").asLong();

        assertThat(secondTaskId).isEqualTo(firstTaskId);
    }

    @Test
    void QUEUED_작업을_취소하면_즉시_CANCELED이고_재취소해도_멱등하다() throws Exception {
        Long siteId = createSite("SITE-B", "물류센터B");
        Long originId = createStation(siteId, "ST-B1", "입고B", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-B2", "출고B", StationType.DROPOFF);

        String createResponse = mockMvc.perform(post("/api/worker/tasks")
                        .header("Idempotency-Key", "idem-key-2")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTaskRequest(originId, destinationId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long taskId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(post("/api/admin/tasks/{taskId}/cancel", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        mockMvc.perform(post("/api/admin/tasks/{taskId}/cancel", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    @Test
    void 완료된_작업을_취소하면_409를_반환한다() throws Exception {
        Long siteId = createSite("SITE-C", "물류센터C");
        Long originId = createStation(siteId, "ST-C1", "입고C", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-C2", "출고C", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-C1", "C1호기");

        String createResponse = mockMvc.perform(post("/api/worker/tasks")
                        .header("Idempotency-Key", "idem-key-3")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTaskRequest(originId, destinationId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long taskId = objectMapper.readTree(createResponse).get("id").asLong();

        // start()는 MOVE_TO_ORIGIN을 시뮬레이터가 자동으로 완료시킨다 (4단계).
        // 남은 CONFIRM_LOAD, (자동 완료되는 MOVE_TO_DESTINATION 포함) CONFIRM_UNLOAD까지
        // completeCurrentStep 두 번이면 작업이 SUCCEEDED에 도달한다.
        Robot robot = robotRepository.findById(robotId).orElseThrow();
        taskService.assignRobot(taskId, robot);
        taskService.start(taskId);
        taskService.completeCurrentStep(taskId);
        taskService.completeCurrentStep(taskId);

        mockMvc.perform(post("/api/admin/tasks/{taskId}/cancel", taskId))
                .andExpect(status().isConflict());
    }

    private Long createSite(String code, String name) throws Exception {
        String response = mockMvc.perform(post("/api/admin/sites")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSiteRequest(code, name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private Long createStation(Long siteId, String code, String name, StationType stationType) throws Exception {
        String response = mockMvc.perform(post("/api/admin/sites/{siteId}/stations", siteId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateStationRequest(code, name, stationType))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private Long createRobot(Long siteId, String code, String name) throws Exception {
        String response = mockMvc.perform(post("/api/admin/robots")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRobotRequest(code, name, null, null, siteId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}

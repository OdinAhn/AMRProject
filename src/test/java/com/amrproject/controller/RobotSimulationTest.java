package com.amrproject.controller;

import com.amrproject.domain.entity.robot.Robot;
import com.amrproject.domain.entity.site.StationType;
import com.amrproject.dto.request.robot.CreateRobotRequest;
import com.amrproject.dto.request.site.CreateSiteRequest;
import com.amrproject.dto.request.site.CreateStationRequest;
import com.amrproject.dto.request.task.CreateTaskRequest;
import com.amrproject.dto.request.robot.InjectFaultRequest;
import com.amrproject.repository.robot.RobotRepository;
import com.amrproject.robot.SimulatedFault;
import com.amrproject.service.task.TaskService;
import com.amrproject.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 4단계 완료 기준(실제 장비 없이 이동·운반 실행)을 검증한다.
 * 배정/시작은 아직 REST로 노출되지 않아 TaskService를 직접 호출한다 (3단계와 동일한 제약).
 */
@AutoConfigureMockMvc
class RobotSimulationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private TaskService taskService;

    @Autowired
    private RobotRepository robotRepository;

    @Test
    void MOVE_단계는_시뮬레이터가_자동으로_완료시켜_다음_단계로_넘어간다() throws Exception {
        Long siteId = createSite("SITE-SIM1", "시뮬레이터 물류센터1");
        Long originId = createStation(siteId, "ST-1", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-2", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-SIM1", "시뮬로봇1");

        Long taskId = createTask(originId, destinationId, "idem-sim-1");

        Robot robot = robotRepository.findById(robotId).orElseThrow();
        taskService.assignRobot(taskId, robot);
        taskService.start(taskId);

        mockMvc.perform(get("/api/worker/tasks/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.steps[0].stepType").value("MOVE_TO_ORIGIN"))
                .andExpect(jsonPath("$.steps[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.steps[1].stepType").value("CONFIRM_LOAD"))
                .andExpect(jsonPath("$.steps[1].status").value("RUNNING"));

        mockMvc.perform(get("/api/admin/robots/{robotId}/state", robotId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motionState").value("IDLE"))
                .andExpect(jsonPath("$.connectionState").value("ONLINE"));
    }

    @Test
    void 명령_실패를_주입하면_작업이_RECOVERY_REQUIRED로_전이한다() throws Exception {
        Long siteId = createSite("SITE-SIM2", "시뮬레이터 물류센터2");
        Long originId = createStation(siteId, "ST-3", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-4", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-SIM2", "시뮬로봇2");

        Long taskId = createTask(originId, destinationId, "idem-sim-2");

        mockMvc.perform(post("/api/admin/robots/{robotId}/simulator/faults", robotId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InjectFaultRequest(SimulatedFault.COMMAND_FAILURE))))
                .andExpect(status().isNoContent());

        Robot robot = robotRepository.findById(robotId).orElseThrow();
        taskService.assignRobot(taskId, robot);
        taskService.start(taskId);

        mockMvc.perform(get("/api/worker/tasks/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECOVERY_REQUIRED"))
                .andExpect(jsonPath("$.steps[0].status").value("FAILED"));
    }

    private Long createTask(Long originId, Long destinationId, String idempotencyKey) throws Exception {
        String response = mockMvc.perform(post("/api/worker/tasks")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTaskRequest(originId, destinationId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
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

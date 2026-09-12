package com.amrproject.controller;

import com.amrproject.domain.entity.robot.RobotCurrentState;
import com.amrproject.domain.entity.robot.RobotLifecycleStatus;
import com.amrproject.domain.entity.site.StationStatus;
import com.amrproject.domain.entity.site.StationType;
import com.amrproject.dto.request.task.AssignRobotRequest;
import com.amrproject.dto.request.robot.CreateRobotConfigurationRequest;
import com.amrproject.dto.request.robot.CreateRobotRequest;
import com.amrproject.dto.request.site.CreateSiteRequest;
import com.amrproject.dto.request.site.CreateStationRequest;
import com.amrproject.dto.request.task.CreateTaskRequest;
import com.amrproject.dto.request.robot.UpdateRobotLifecycleStatusRequest;
import com.amrproject.dto.request.site.UpdateStationRequest;
import com.amrproject.repository.robot.RobotCurrentStateRepository;
import com.amrproject.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 7단계 완료 기준(다중 로봇 경쟁·배터리 시나리오 통과)을 검증한다.
 */
@AutoConfigureMockMvc
class MultiRobotDispatchTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private RobotCurrentStateRepository robotCurrentStateRepository;

    @Test
    void 대기중인_두_작업을_디스패치_사이클_한번으로_서로_다른_로봇에_배정한다() throws Exception {
        Long siteId = createSite("SITE-MRD1", "다중로봇1");
        Long originId = createStation(siteId, "ST-1", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-2", "출고", StationType.DROPOFF);
        Long robot1 = createInServiceRobot(siteId, "ROBOT-MRD1", "로봇1");
        Long robot2 = createInServiceRobot(siteId, "ROBOT-MRD2", "로봇2");

        Long task1 = createTask(originId, destinationId, "idem-mrd-1");
        Long task2 = createTask(originId, destinationId, "idem-mrd-2");

        String response = mockMvc.perform(post("/api/admin/sites/{siteId}/dispatch", siteId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var result = objectMapper.readTree(response);
        assertThat(result.get("assignedTaskIds").size()).isEqualTo(2);

        mockMvc.perform(get("/api/worker/tasks/{taskId}", task1))
                .andExpect(jsonPath("$.status").value("RUNNING"));
        mockMvc.perform(get("/api/worker/tasks/{taskId}", task2))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void 목적지_station이_가득_차면_거기로_보내는_시도는_409이고_비워지면_성공한다() throws Exception {
        Long siteId = createSite("SITE-MRD2", "다중로봇2");
        Long originId = createStation(siteId, "ST-3", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-4", "출고", StationType.DROPOFF);
        setMaxConcurrentRobots(destinationId, 1);
        Long robot1 = createInServiceRobot(siteId, "ROBOT-MRD3", "로봇3");
        Long robot2 = createInServiceRobot(siteId, "ROBOT-MRD4", "로봇4");

        Long task1 = createTask(originId, destinationId, "idem-mrd-3");
        assignRobot(task1, robot1);
        mockMvc.perform(post("/api/worker/tasks/{taskId}/dispatch", task1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[3].stepType").value("CONFIRM_UNLOAD"))
                .andExpect(jsonPath("$.steps[3].status").value("RUNNING"));

        Long task2 = createTask(originId, destinationId, "idem-mrd-4");
        assignRobot(task2, robot2);
        mockMvc.perform(post("/api/worker/tasks/{taskId}/dispatch", task2))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/worker/tasks/{taskId}/confirm-unload", task1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mockMvc.perform(post("/api/worker/tasks/{taskId}/dispatch", task2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[3].stepType").value("CONFIRM_UNLOAD"))
                .andExpect(jsonPath("$.steps[3].status").value("RUNNING"));
    }

    @Test
    void 배터리가_낮은_유휴_로봇은_디스패치_사이클에서_자동으로_충전된다() throws Exception {
        Long siteId = createSite("SITE-MRD3", "다중로봇3");
        createStation(siteId, "ST-5", "충전소", StationType.CHARGING);
        Long robotId = createInServiceRobot(siteId, "ROBOT-MRD5", "로봇5");

        mockMvc.perform(post("/api/admin/robots/{robotId}/configurations", robotId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRobotConfigurationRequest(
                                new BigDecimal("50.0"), new BigDecimal("1.5"), new BigDecimal("500.0"), 20, 90
                        ))))
                .andExpect(status().isCreated());

        lowerBattery(robotId, 10);

        String response = mockMvc.perform(post("/api/admin/sites/{siteId}/dispatch", siteId))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString();
        var result = objectMapper.readTree(response);
        assertThat(result.get("chargeTaskIds").size()).isEqualTo(1);
        long chargeTaskId = result.get("chargeTaskIds").get(0).asLong();

        mockMvc.perform(get("/api/admin/tasks/{taskId}", chargeTaskId))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.steps[0].stepType").value("MOVE_TO_CHARGING_STATION"))
                .andExpect(jsonPath("$.steps[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.steps[1].stepType").value("DOCK_AND_CHARGE"))
                .andExpect(jsonPath("$.steps[1].status").value("SUCCEEDED"));

        mockMvc.perform(get("/api/admin/robots/{robotId}/state", robotId))
                .andExpect(jsonPath("$.batteryPct").value(90));
    }

    private void lowerBattery(Long robotId, int batteryPct) {
        RobotCurrentState state = robotCurrentStateRepository.findById(robotId).orElseThrow();
        state.report(
                state.getConnectionState(), state.getOperationMode(), state.getMotionState(), state.getSafetyState(),
                state.getLoadState(), state.getLocalizationState(), batteryPct, Instant.now()
        );
        robotCurrentStateRepository.save(state);
    }

    private void assignRobot(Long taskId, Long robotId) throws Exception {
        mockMvc.perform(post("/api/admin/tasks/{taskId}/assign", taskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignRobotRequest(robotId))))
                .andExpect(status().isOk());
    }

    private void setMaxConcurrentRobots(Long stationId, int maxConcurrentRobots) throws Exception {
        mockMvc.perform(patch("/api/admin/stations/{stationId}", stationId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateStationRequest(null, StationStatus.ACTIVE, maxConcurrentRobots))))
                .andExpect(status().isOk());
    }

    private Long createInServiceRobot(Long siteId, String code, String name) throws Exception {
        Long robotId = createRobot(siteId, code, name);
        mockMvc.perform(patch("/api/admin/robots/{robotId}/lifecycle-status", robotId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRobotLifecycleStatusRequest(RobotLifecycleStatus.IN_SERVICE))))
                .andExpect(status().isOk());
        return robotId;
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

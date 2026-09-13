package com.amrproject.controller;

import com.amrproject.domain.entity.site.StationType;
import com.amrproject.dto.request.task.AssignRobotRequest;
import com.amrproject.dto.request.robot.CreateRobotRequest;
import com.amrproject.dto.request.site.CreateSiteRequest;
import com.amrproject.dto.request.site.CreateStationRequest;
import com.amrproject.dto.request.task.CreateTaskRequest;
import com.amrproject.dto.request.robot.InjectFaultRequest;
import com.amrproject.dto.request.task.RecoverTaskRequest;
import com.amrproject.dto.request.task.RecoveryResolution;
import com.amrproject.robot.SimulatedFault;
import com.amrproject.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 5단계 완료 기준(API만으로 운반 완료, 기본 장애 복구)을 REST 레벨에서 검증한다.
 * 배정→시작→적재확인→하역까지 전부 REST 호출만으로 진행한다 (3~4단계 테스트와 달리
 * TaskService를 직접 호출하지 않음).
 */
@AutoConfigureMockMvc
class TransportFlowTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Test
    void 호출부터_하역까지_API만으로_운반을_완료하고_배정이_해제되어_로봇을_재사용할_수_있다() throws Exception {
        Long siteId = createSite("SITE-FLOW1", "물류센터FLOW1");
        Long originId = createStation(siteId, "ST-F1", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-F2", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-FLOW1", "흐름로봇1");

        Long taskId = createTask(originId, destinationId, "idem-flow-1");

        mockMvc.perform(post("/api/admin/tasks/{taskId}/assign", taskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignRobotRequest(robotId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.steps[0].stepType").value("MOVE_TO_ORIGIN"))
                .andExpect(jsonPath("$.steps[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.steps[1].stepType").value("CONFIRM_LOAD"))
                .andExpect(jsonPath("$.steps[1].status").value("RUNNING"))
                .andExpect(jsonPath("$.originStationName").value("입고"))
                .andExpect(jsonPath("$.destinationStationName").value("출고"))
                .andExpect(jsonPath("$.assignedRobotId").value(robotId))
                .andExpect(jsonPath("$.assignedRobotCode").value("ROBOT-FLOW1"))
                .andExpect(jsonPath("$.allowedActions[0]").value("CANCEL"))
                .andExpect(jsonPath("$.allowedActions[1]").value("DISPATCH"))
                .andExpect(jsonPath("$.allowedActions[2]").value("REPORT_UNAVAILABLE"))
                .andExpect(jsonPath("$.allowedActions.length()").value(3));

        mockMvc.perform(post("/api/worker/tasks/{taskId}/dispatch", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.steps[2].stepType").value("MOVE_TO_DESTINATION"))
                .andExpect(jsonPath("$.steps[2].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.steps[3].stepType").value("CONFIRM_UNLOAD"))
                .andExpect(jsonPath("$.steps[3].status").value("RUNNING"))
                .andExpect(jsonPath("$.allowedActions[0]").value("CANCEL"))
                .andExpect(jsonPath("$.allowedActions[1]").value("CONFIRM_UNLOAD"))
                .andExpect(jsonPath("$.allowedActions[2]").value("REPORT_UNAVAILABLE"))
                .andExpect(jsonPath("$.allowedActions.length()").value(3));

        mockMvc.perform(post("/api/worker/tasks/{taskId}/confirm-unload", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.steps[3].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.allowedActions.length()").value(0));

        // 배정이 COMPLETED로 정리되지 않았다면 같은 로봇의 재배정이 409로 막혀야 한다.
        Long secondTaskId = createTask(originId, destinationId, "idem-flow-2");
        mockMvc.perform(post("/api/admin/tasks/{taskId}/assign", secondTaskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignRobotRequest(robotId))))
                .andExpect(status().isOk());
    }

    @Test
    void 이미_지나간_단계에_dispatch를_재호출하면_409_대신_현재_상태를_반환한다() throws Exception {
        Long siteId = createSite("SITE-FLOW2", "물류센터FLOW2");
        Long originId = createStation(siteId, "ST-F3", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-F4", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-FLOW2", "흐름로봇2");
        Long taskId = createTask(originId, destinationId, "idem-flow-3");

        mockMvc.perform(post("/api/admin/tasks/{taskId}/assign", taskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignRobotRequest(robotId))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/worker/tasks/{taskId}/dispatch", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[3].stepType").value("CONFIRM_UNLOAD"))
                .andExpect(jsonPath("$.steps[3].status").value("RUNNING"));

        // CONFIRM_LOAD는 이미 SUCCEEDED - 재요청은 409가 아니라 현재 상태를 그대로 반환한다.
        mockMvc.perform(post("/api/worker/tasks/{taskId}/dispatch", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.steps[3].status").value("RUNNING"));
    }

    @Test
    void 명령_실패_주입_후_RESUME하면_재시도로_다음_단계까지_진행한다() throws Exception {
        Long siteId = createSite("SITE-FLOW3", "물류센터FLOW3");
        Long originId = createStation(siteId, "ST-F5", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-F6", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-FLOW3", "흐름로봇3");
        Long taskId = createTask(originId, destinationId, "idem-flow-4");

        mockMvc.perform(post("/api/admin/robots/{robotId}/simulator/faults", robotId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InjectFaultRequest(SimulatedFault.COMMAND_FAILURE))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/admin/tasks/{taskId}/assign", taskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignRobotRequest(robotId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECOVERY_REQUIRED"))
                .andExpect(jsonPath("$.steps[0].status").value("FAILED"));

        // 장애는 1회성 소비이므로 재시도는 성공해야 한다.
        mockMvc.perform(post("/api/admin/tasks/{taskId}/recover", taskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecoverTaskRequest(RecoveryResolution.RESUME))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.steps[0].stepType").value("MOVE_TO_ORIGIN"))
                .andExpect(jsonPath("$.steps[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.steps[1].stepType").value("CONFIRM_LOAD"))
                .andExpect(jsonPath("$.steps[1].status").value("RUNNING"));
    }

    @Test
    void 명령_실패_주입_후_FAIL하면_작업이_최종실패하고_로봇_배정이_해제된다() throws Exception {
        Long siteId = createSite("SITE-FLOW4", "물류센터FLOW4");
        Long originId = createStation(siteId, "ST-F7", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-F8", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-FLOW4", "흐름로봇4");
        Long taskId = createTask(originId, destinationId, "idem-flow-5");

        mockMvc.perform(post("/api/admin/robots/{robotId}/simulator/faults", robotId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InjectFaultRequest(SimulatedFault.COMMAND_FAILURE))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/admin/tasks/{taskId}/assign", taskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignRobotRequest(robotId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECOVERY_REQUIRED"));

        mockMvc.perform(post("/api/admin/tasks/{taskId}/recover", taskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecoverTaskRequest(RecoveryResolution.FAIL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        // 배정이 RELEASED로 정리되지 않았다면 같은 로봇의 재배정이 409로 막혀야 한다.
        Long secondTaskId = createTask(originId, destinationId, "idem-flow-6");
        mockMvc.perform(post("/api/admin/tasks/{taskId}/assign", secondTaskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignRobotRequest(robotId))))
                .andExpect(status().isOk());
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

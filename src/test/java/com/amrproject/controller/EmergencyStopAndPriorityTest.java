package com.amrproject.controller;

import com.amrproject.domain.entity.alarm.AlarmSourceType;
import com.amrproject.domain.entity.alarm.AlarmStatus;
import com.amrproject.domain.entity.alarm.AlarmType;
import com.amrproject.domain.entity.robot.RobotLifecycleStatus;
import com.amrproject.domain.entity.site.StationType;
import com.amrproject.dto.request.robot.CreateRobotRequest;
import com.amrproject.dto.request.robot.UpdateRobotLifecycleStatusRequest;
import com.amrproject.dto.request.site.CreateSiteRequest;
import com.amrproject.dto.request.site.CreateStationRequest;
import com.amrproject.dto.request.task.AssignRobotRequest;
import com.amrproject.dto.request.task.ChangeTaskPriorityRequest;
import com.amrproject.dto.request.task.CreateTaskRequest;
import com.amrproject.dto.request.task.RecoverTaskRequest;
import com.amrproject.dto.request.task.RecoveryResolution;
import com.amrproject.repository.alarm.AlarmRepository;
import com.amrproject.service.task.DispatchService;
import com.amrproject.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 33단계: 로봇 비상정지(작업 중단·배정 제외·명령 거부·알람·현장 해제), 물류센터 전체 비상정지,
 * 작업 일시정지·재개, 작업 우선순위(생성·변경·배정 순서)를 검증한다.
 */
@AutoConfigureMockMvc
class EmergencyStopAndPriorityTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private DispatchService dispatchService;

    @Autowired
    private AlarmRepository alarmRepository;

    @Test
    void 비상정지하면_작업이_복구필요가_되고_해제될_때까지_명령을_거부하며_배정에서_빠진다() throws Exception {
        Long siteId = createSite("SITE-ES1", "비상정지센터1");
        Long originId = createStation(siteId, "ST-ES1", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-ES2", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-ES1", "비상정지로봇1");
        setInService(robotId);
        Long taskId = createTask(originId, destinationId, "idem-es-1", null);
        assign(taskId, robotId);

        mockMvc.perform(post("/api/admin/robots/{robotId}/emergency-stop", robotId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.robotId").value(robotId))
                .andExpect(jsonPath("$.commandStatus").value("SUCCEEDED"));

        mockMvc.perform(get("/api/admin/robots/{robotId}/state", robotId))
                .andExpect(jsonPath("$.safetyState").value("EMERGENCY_STOP"))
                .andExpect(jsonPath("$.motionState").value("IDLE"));
        mockMvc.perform(get("/api/admin/tasks/{taskId}", taskId))
                .andExpect(jsonPath("$.status").value("RECOVERY_REQUIRED"));
        assertThat(openAlarm(AlarmType.ROBOT_EMERGENCY_STOP, robotId)).isTrue();

        // 정지 상태에서 재개하면 로봇이 명령을 거부해 다시 복구 필요가 된다 — 여기선 적재 대기 단계라 명령이 없어 RUNNING이 되므로,
        // 보내기(이동 명령)로 거부를 확인한다.
        mockMvc.perform(post("/api/admin/tasks/{taskId}/recover", taskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecoverTaskRequest(RecoveryResolution.RESUME))))
                .andExpect(jsonPath("$.status").value("RUNNING"));
        mockMvc.perform(post("/api/worker/tasks/{taskId}/dispatch", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECOVERY_REQUIRED"));

        // 같은 물류센터의 대기 작업은 비상정지된 로봇에 배정되지 않는다.
        mockMvc.perform(post("/api/admin/tasks/{taskId}/recover", taskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecoverTaskRequest(RecoveryResolution.FAIL))))
                .andExpect(jsonPath("$.status").value("FAILED"));
        Long queuedTaskId = createTask(originId, destinationId, "idem-es-2", null);
        assertThat(dispatchService.runCycle(siteId).assignedTaskIds()).doesNotContain(queuedTaskId);

        // 현장 해제(시뮬레이터) 후에는 알람이 해소되고 다시 배정된다.
        mockMvc.perform(post("/api/admin/robots/{robotId}/simulator/emergency-stop-release", robotId))
                .andExpect(status().isNoContent());
        assertThat(openAlarm(AlarmType.ROBOT_EMERGENCY_STOP, robotId)).isFalse();
        assertThat(dispatchService.runCycle(siteId).assignedTaskIds()).contains(queuedTaskId);
    }

    @Test
    void 물류센터_전체_비상정지는_운영중인_로봇만_모두_멈춘다() throws Exception {
        Long siteId = createSite("SITE-ES2", "비상정지센터2");
        Long firstRobotId = createRobot(siteId, "ROBOT-ES2", "비상정지로봇2");
        Long secondRobotId = createRobot(siteId, "ROBOT-ES3", "비상정지로봇3");
        createRobot(siteId, "ROBOT-ES4", "등록만된로봇");
        setInService(firstRobotId);
        setInService(secondRobotId);

        String response = mockMvc.perform(post("/api/admin/sites/{siteId}/emergency-stop", siteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> stopped = objectMapper.readValue(response, List.class);
        assertThat(stopped).extracting(item -> ((Number) item.get("robotId")).longValue()).containsExactlyInAnyOrder(firstRobotId, secondRobotId);

        mockMvc.perform(get("/api/admin/sites/{siteId}/overview", siteId))
                .andExpect(jsonPath("$.robots[?(@.robotId == " + firstRobotId + ")].safetyState").value("EMERGENCY_STOP"))
                .andExpect(jsonPath("$.robots[?(@.robotId == " + secondRobotId + ")].safetyState").value("EMERGENCY_STOP"));
    }

    @Test
    void 일시정지하면_작업자_버튼이_사라지고_재개하면_이어서_진행된다() throws Exception {
        Long siteId = createSite("SITE-PS1", "일시정지센터1");
        Long originId = createStation(siteId, "ST-PS1", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-PS2", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-PS1", "일시정지로봇1");
        setInService(robotId);
        Long taskId = createTask(originId, destinationId, "idem-ps-1", null);
        assign(taskId, robotId);

        mockMvc.perform(post("/api/admin/tasks/{taskId}/pause", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"))
                .andExpect(jsonPath("$.allowedActions[?(@ == 'DISPATCH')]").isEmpty())
                .andExpect(jsonPath("$.allowedActions[?(@ == 'RESUME')]").isNotEmpty());
        // 멱등: 다시 눌러도 그대로
        mockMvc.perform(post("/api/admin/tasks/{taskId}/pause", taskId)).andExpect(jsonPath("$.status").value("PAUSED"));
        mockMvc.perform(post("/api/worker/tasks/{taskId}/dispatch", taskId)).andExpect(status().isConflict());

        mockMvc.perform(post("/api/admin/tasks/{taskId}/resume", taskId))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.allowedActions[?(@ == 'DISPATCH')]").isNotEmpty());
        mockMvc.perform(post("/api/worker/tasks/{taskId}/dispatch", taskId)).andExpect(status().isOk());

        String events = mockMvc.perform(get("/api/admin/tasks/{taskId}/events", taskId)).andReturn().getResponse().getContentAsString();
        assertThat(events).contains("\"PAUSED\"").contains("\"RESUMED\"");
    }

    @Test
    void 우선순위가_높은_대기_작업부터_배정되고_배정된_작업의_우선순위는_바꿀_수_없다() throws Exception {
        Long siteId = createSite("SITE-PR1", "우선순위센터1");
        Long originId = createStation(siteId, "ST-PR1", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-PR2", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-PR1", "우선순위로봇1");
        setInService(robotId);

        Long normalTaskId = createTask(originId, destinationId, "idem-pr-1", null);
        Long laterTaskId = createTask(originId, destinationId, "idem-pr-2", 0);
        mockMvc.perform(patch("/api/admin/tasks/{taskId}/priority", laterTaskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeTaskPriorityRequest(9))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value(9));

        // 로봇이 한 대뿐 — 나중에 들어왔지만 우선순위가 높은 작업이 먼저 배정된다.
        assertThat(dispatchService.runCycle(siteId).assignedTaskIds()).containsExactly(laterTaskId);
        mockMvc.perform(get("/api/admin/tasks/{taskId}", normalTaskId)).andExpect(jsonPath("$.status").value("QUEUED"));

        mockMvc.perform(patch("/api/admin/tasks/{taskId}/priority", laterTaskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeTaskPriorityRequest(1))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/worker/tasks")
                        .header("Idempotency-Key", "idem-pr-3")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTaskRequest(originId, destinationId, 10))))
                .andExpect(status().isBadRequest());
    }

    private boolean openAlarm(AlarmType type, Long robotId) {
        return alarmRepository.findFirstByAlarmTypeAndSourceTypeAndSourceIdAndStatusIn(
                type, AlarmSourceType.ROBOT, robotId, List.of(AlarmStatus.OPEN, AlarmStatus.ACKNOWLEDGED)).isPresent();
    }

    private void assign(Long taskId, Long robotId) throws Exception {
        mockMvc.perform(post("/api/admin/tasks/{taskId}/assign", taskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignRobotRequest(robotId))))
                .andExpect(status().isOk());
    }

    private void setInService(Long robotId) throws Exception {
        mockMvc.perform(patch("/api/admin/robots/{robotId}/lifecycle-status", robotId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRobotLifecycleStatusRequest(RobotLifecycleStatus.IN_SERVICE))))
                .andExpect(status().isOk());
    }

    private Long createTask(Long originId, Long destinationId, String idempotencyKey, Integer priority) throws Exception {
        String response = mockMvc.perform(post("/api/worker/tasks")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTaskRequest(originId, destinationId, priority))))
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

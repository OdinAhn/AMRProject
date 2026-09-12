package com.amrproject.controller;

import com.amrproject.domain.entity.StationType;
import com.amrproject.dto.request.AssignRobotRequest;
import com.amrproject.dto.request.CreateRobotRequest;
import com.amrproject.dto.request.CreateSiteRequest;
import com.amrproject.dto.request.CreateStationRequest;
import com.amrproject.dto.request.CreateTaskRequest;
import com.amrproject.dto.request.RecoverTaskRequest;
import com.amrproject.dto.request.RecoveryResolution;
import com.amrproject.dto.request.ReportUnavailableRequest;
import com.amrproject.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 8단계 완료 기준(중복 물리 동작 없이 복구 또는 개입 요청)을 검증한다.
 */
@AutoConfigureMockMvc
class OperationalRecoveryTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Test
    void 적재_전에_취소하면_즉시_CANCELED된다() throws Exception {
        Long siteId = createSite("SITE-OR1", "복구물류센터1");
        Long originId = createStation(siteId, "ST-OR1", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-OR2", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-OR1", "복구로봇1");
        Long taskId = createTask(originId, destinationId, "idem-or-1");

        assignRobot(taskId, robotId);
        mockMvc.perform(get("/api/worker/tasks/{taskId}", taskId))
                .andExpect(jsonPath("$.steps[1].stepType").value("CONFIRM_LOAD"))
                .andExpect(jsonPath("$.steps[1].status").value("RUNNING"));

        mockMvc.perform(post("/api/admin/tasks/{taskId}/cancel", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    @Test
    void 적재_중에_취소하면_RECOVERY_REQUIRED로_가고_RESUME하면_하역완료로_이어진다() throws Exception {
        Long siteId = createSite("SITE-OR2", "복구물류센터2");
        Long originId = createStation(siteId, "ST-OR3", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-OR4", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-OR2", "복구로봇2");
        Long taskId = createTask(originId, destinationId, "idem-or-2");

        assignRobot(taskId, robotId);
        mockMvc.perform(post("/api/worker/tasks/{taskId}/dispatch", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[3].stepType").value("CONFIRM_UNLOAD"))
                .andExpect(jsonPath("$.steps[3].status").value("RUNNING"));

        mockMvc.perform(post("/api/admin/tasks/{taskId}/cancel", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECOVERY_REQUIRED"));

        mockMvc.perform(post("/api/admin/tasks/{taskId}/recover", taskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecoverTaskRequest(RecoveryResolution.RESUME))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.steps[3].stepType").value("CONFIRM_UNLOAD"))
                .andExpect(jsonPath("$.steps[3].status").value("RUNNING"));

        mockMvc.perform(post("/api/worker/tasks/{taskId}/confirm-unload", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test
    void 작업불가_보고는_QUEUED면_즉시취소_배정된상태면_복구필요로_보내고_사유를_기록한다() throws Exception {
        Long siteId = createSite("SITE-OR3", "복구물류센터3");
        Long originId = createStation(siteId, "ST-OR5", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-OR6", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-OR3", "복구로봇3");

        Long queuedTaskId = createTask(originId, destinationId, "idem-or-3");
        mockMvc.perform(post("/api/worker/tasks/{taskId}/report-unavailable", queuedTaskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReportUnavailableRequest("작업장 폐쇄"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        Long runningTaskId = createTask(originId, destinationId, "idem-or-4");
        assignRobot(runningTaskId, robotId);
        mockMvc.perform(post("/api/worker/tasks/{taskId}/report-unavailable", runningTaskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReportUnavailableRequest("통로 막힘"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECOVERY_REQUIRED"));

        String eventsResponse = mockMvc.perform(get("/api/admin/tasks/{taskId}/events", runningTaskId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var events = objectMapper.readTree(eventsResponse);
        boolean found = false;
        for (var event : events) {
            if ("REPORT_UNAVAILABLE".equals(event.get("eventType").asString())) {
                assertThat(event.get("payload").asString()).isEqualTo("통로 막힘");
                found = true;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void 관리자_작업목록을_상태와_site로_필터링해_조회할_수_있다() throws Exception {
        Long siteId = createSite("SITE-OR4", "복구물류센터4");
        Long originId = createStation(siteId, "ST-OR7", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-OR8", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-OR4", "복구로봇4");

        Long queuedTaskId = createTask(originId, destinationId, "idem-or-5");
        Long needsAttentionTaskId = createTask(originId, destinationId, "idem-or-6");
        assignRobot(needsAttentionTaskId, robotId);
        mockMvc.perform(post("/api/worker/tasks/{taskId}/report-unavailable", needsAttentionTaskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReportUnavailableRequest("로봇 점검 필요"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/tasks")
                        .param("siteId", String.valueOf(siteId))
                        .param("status", "RECOVERY_REQUIRED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(needsAttentionTaskId));

        mockMvc.perform(get("/api/admin/tasks")
                        .param("siteId", String.valueOf(siteId))
                        .param("status", "QUEUED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(queuedTaskId));
    }

    private void assignRobot(Long taskId, Long robotId) throws Exception {
        mockMvc.perform(post("/api/admin/tasks/{taskId}/assign", taskId)
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

package com.amrproject.controller;

import com.amrproject.domain.entity.robot.RobotLifecycleStatus;
import com.amrproject.domain.entity.site.StationType;
import com.amrproject.dto.request.robot.CreateRobotRequest;
import com.amrproject.dto.request.robot.UpdateRobotLifecycleStatusRequest;
import com.amrproject.dto.request.site.CreateSiteRequest;
import com.amrproject.dto.request.site.CreateStationRequest;
import com.amrproject.dto.request.task.AssignRobotRequest;
import com.amrproject.dto.request.task.CreateTaskRequest;
import com.amrproject.dto.request.task.RecoverTaskRequest;
import com.amrproject.dto.request.task.RecoveryResolution;
import com.amrproject.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 34단계: 물류센터 운영 통계 — 처리량·성공률·평균 처리/대기 시간·로봇 가동률·알람 집계와 시간대별 날짜 경계, 기간 검증.
 *
 * <p>시각은 실제 흐름으로 작업을 끝낸 뒤 SQL로 과거로 옮겨 정확한 초 단위 값을 만든다. JdbcTemplate에 Java 시각을 넘기면
 * Hibernate의 UTC 저장과 어긋나므로 date_sub와 UTC 리터럴만 쓴다.
 */
@AutoConfigureMockMvc
class SiteStatisticsTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 기간_안의_작업_처리량_성공률_평균시간_로봇가동률_알람을_집계한다() throws Exception {
        Long siteId = createSite("SITE-STAT1", "통계센터1");
        Long originId = createStation(siteId, "ST-STAT1", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-STAT2", "출고", StationType.DROPOFF);
        Long firstRobotId = createRobot(siteId, "ROBOT-STAT1", "통계로봇1");
        Long secondRobotId = createRobot(siteId, "ROBOT-STAT2", "통계로봇2");
        setInService(firstRobotId);
        setInService(secondRobotId);

        // 성공: 로봇1이 끝까지 운반. 생성→배정 600초, 배정→완료 3600초로 옮긴다.
        Long succeededTaskId = createTask(originId, destinationId, "idem-stat-1");
        assign(succeededTaskId, firstRobotId);
        mockMvc.perform(post("/api/worker/tasks/{taskId}/dispatch", succeededTaskId)).andExpect(status().isOk());
        mockMvc.perform(post("/api/worker/tasks/{taskId}/confirm-unload", succeededTaskId))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        shiftTimings(succeededTaskId, 4200, 3600);

        // 실패: 로봇2 비상정지 → 복구 필요 → 최종 실패. 생성→배정 600초, 배정→종료 400초.
        Long failedTaskId = createTask(originId, destinationId, "idem-stat-2");
        assign(failedTaskId, secondRobotId);
        mockMvc.perform(post("/api/admin/robots/{robotId}/emergency-stop", secondRobotId)).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/tasks/{taskId}/recover", failedTaskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecoverTaskRequest(RecoveryResolution.FAIL))))
                .andExpect(jsonPath("$.status").value("FAILED"));
        shiftTimings(failedTaskId, 1000, 400);

        // 배정 전 취소 1건, 대기 1건
        Long canceledTaskId = createTask(originId, destinationId, "idem-stat-3");
        mockMvc.perform(post("/api/admin/tasks/{taskId}/cancel", canceledTaskId)).andExpect(jsonPath("$.status").value("CANCELED"));
        createTask(originId, destinationId, "idem-stat-4");

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String response = mockMvc.perform(get("/api/admin/sites/{siteId}/statistics", siteId)
                        .param("from", today.minusDays(2).toString())
                        .param("to", today.toString())
                        .param("zone", "UTC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zone").value("UTC"))
                .andExpect(jsonPath("$.tasks.created").value(4))
                .andExpect(jsonPath("$.tasks.succeeded").value(1))
                .andExpect(jsonPath("$.tasks.failed").value(1))
                .andExpect(jsonPath("$.tasks.canceled").value(1))
                .andExpect(jsonPath("$.tasks.successRatePct").value(50.0))
                .andExpect(jsonPath("$.tasks.avgLeadTimeSeconds").value(4200))
                // 취소 작업은 배정된 적이 없어 대기 시간 평균에서 빠진다.
                .andExpect(jsonPath("$.tasks.avgWaitSeconds").value(600))
                .andExpect(jsonPath("$.daily.length()").value(3))
                .andExpect(jsonPath("$.robots[0].code").value("ROBOT-STAT1"))
                .andExpect(jsonPath("$.robots[0].completedTasks").value(1))
                .andExpect(jsonPath("$.robots[0].busySeconds").value(3600))
                .andExpect(jsonPath("$.robots[0].utilizationPct", greaterThan(0.0)))
                .andExpect(jsonPath("$.robots[1].completedTasks").value(0))
                .andExpect(jsonPath("$.robots[1].busySeconds").value(400))
                .andReturn().getResponse().getContentAsString();

        // 시각을 과거로 옮겨 자정 직후 실행이면 전날로 넘어갈 수 있어, 일별 값은 합계로 확인한다.
        JsonNode body = objectMapper.readTree(response);
        long createdSum = 0;
        long succeededSum = 0;
        for (JsonNode day : body.get("daily")) {
            createdSum += day.get("created").asLong();
            succeededSum += day.get("succeeded").asLong();
        }
        assertThat(createdSum).isEqualTo(4);
        assertThat(succeededSum).isEqualTo(1);

        Map<String, Long> alarmCounts = new HashMap<>();
        for (JsonNode alarm : body.get("alarms")) {
            alarmCounts.merge(alarm.get("alarmType").asString(), alarm.get("count").asLong(), Long::sum);
        }
        assertThat(alarmCounts).containsEntry("ROBOT_EMERGENCY_STOP", 1L).containsKey("TASK_RECOVERY_REQUIRED");
    }

    @Test
    void 날짜_경계는_요청한_시간대로_나눈다() throws Exception {
        Long siteId = createSite("SITE-STAT2", "통계센터2");
        Long originId = createStation(siteId, "ST-STAT3", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-STAT4", "출고", StationType.DROPOFF);
        Long taskId = createTask(originId, destinationId, "idem-stat-5");
        mockMvc.perform(post("/api/admin/tasks/{taskId}/cancel", taskId)).andExpect(jsonPath("$.status").value("CANCELED"));
        // UTC 2026-01-01 15:30 = 서울 2026-01-02 00:30
        jdbcTemplate.update("update tasks set created_at = '2026-01-01 15:00:00', completed_at = '2026-01-01 15:30:00' where id = ?", taskId);

        mockMvc.perform(get("/api/admin/sites/{siteId}/statistics", siteId)
                        .param("from", "2026-01-02").param("to", "2026-01-02").param("zone", "Asia/Seoul"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.created").value(1))
                .andExpect(jsonPath("$.tasks.canceled").value(1))
                .andExpect(jsonPath("$.tasks.successRatePct").doesNotExist())
                .andExpect(jsonPath("$.daily[0].date").value("2026-01-02"))
                .andExpect(jsonPath("$.daily[0].canceled").value(1));

        mockMvc.perform(get("/api/admin/sites/{siteId}/statistics", siteId)
                        .param("from", "2026-01-02").param("to", "2026-01-02").param("zone", "UTC"))
                .andExpect(jsonPath("$.tasks.created").value(0))
                .andExpect(jsonPath("$.tasks.canceled").value(0));
    }

    @Test
    void 기간과_시간대가_잘못되면_400이고_생략하면_최근_7일이다() throws Exception {
        Long siteId = createSite("SITE-STAT3", "통계센터3");

        mockMvc.perform(get("/api/admin/sites/{siteId}/statistics", siteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.daily.length()").value(7))
                .andExpect(jsonPath("$.tasks.avgLeadTimeSeconds").doesNotExist())
                .andExpect(jsonPath("$.robots.length()").value(0));

        mockMvc.perform(get("/api/admin/sites/{siteId}/statistics", siteId).param("from", "2026-02-01").param("to", "2026-01-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STATISTICS_RANGE_INVALID"));
        mockMvc.perform(get("/api/admin/sites/{siteId}/statistics", siteId).param("from", "2026-01-01").param("to", "2026-04-03"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STATISTICS_RANGE_INVALID"));
        mockMvc.perform(get("/api/admin/sites/{siteId}/statistics", siteId).param("zone", "Mars/Base"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STATISTICS_ZONE_INVALID"));
        mockMvc.perform(get("/api/admin/sites/{siteId}/statistics", siteId).param("from", "yesterday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/admin/sites/{siteId}/statistics", 999999L))
                .andExpect(status().isNotFound());
    }

    /** 작업 생성을 완료 leadSeconds초 전으로, 배정 구간을 완료 직전 busySeconds초로 옮긴다. */
    private void shiftTimings(Long taskId, int leadSeconds, int busySeconds) {
        jdbcTemplate.update("update tasks set created_at = date_sub(completed_at, interval ? second) where id = ?", leadSeconds, taskId);
        jdbcTemplate.update("""
                update task_assignments a join tasks t on t.id = a.task_id
                set a.assigned_at = date_sub(t.completed_at, interval ? second), a.released_at = t.completed_at
                where a.task_id = ?
                """, busySeconds, taskId);
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

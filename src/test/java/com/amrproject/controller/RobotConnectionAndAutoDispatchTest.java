package com.amrproject.controller;

import com.amrproject.domain.entity.alarm.AlarmSourceType;
import com.amrproject.domain.entity.alarm.AlarmStatus;
import com.amrproject.domain.entity.alarm.AlarmType;
import com.amrproject.domain.entity.robot.ConnectionState;
import com.amrproject.domain.entity.robot.RobotLifecycleStatus;
import com.amrproject.domain.entity.site.StationType;
import com.amrproject.dto.request.robot.CreateRobotRequest;
import com.amrproject.dto.request.robot.UpdateRobotLifecycleStatusRequest;
import com.amrproject.dto.request.site.CreateSiteRequest;
import com.amrproject.dto.request.site.CreateStationRequest;
import com.amrproject.dto.request.task.CreateTaskRequest;
import com.amrproject.repository.alarm.AlarmRepository;
import com.amrproject.repository.robot.RobotCurrentStateRepository;
import com.amrproject.service.robot.RobotConnectionService;
import com.amrproject.service.task.AutoDispatchScheduler;
import com.amrproject.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 29단계: 자동 디스패치 스케줄러와 로봇 연결 감시를 검증한다. 테스트 프로필에서는 두 스케줄러의
 * 주기 실행이 꺼져 있어(app.*.enabled=false) 스케줄러가 부르는 메서드를 직접 호출한다.
 */
@AutoConfigureMockMvc
class RobotConnectionAndAutoDispatchTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private AutoDispatchScheduler autoDispatchScheduler;

    @Autowired
    private RobotConnectionService robotConnectionService;

    @Autowired
    private RobotCurrentStateRepository robotCurrentStateRepository;

    @Autowired
    private AlarmRepository alarmRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 자동_디스패치는_관리자_호출_없이_대기_작업을_운영중_로봇에_배정한다() throws Exception {
        Long siteId = createSite("SITE-AUTO1", "자동배정센터1");
        Long originId = createStation(siteId, "ST-AUTO1", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-AUTO2", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-AUTO1", "자동로봇1");
        setInService(robotId);
        Long taskId = createTask(originId, destinationId, "idem-auto-1");

        autoDispatchScheduler.dispatchActiveSites();

        mockMvc.perform(get("/api/admin/tasks/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.assignedRobotId").value(robotId));
    }

    @Test
    void 하트비트가_끊긴_운영중_로봇은_OFFLINE이_되고_알람이_발생하며_텔레메트리가_오면_복구된다() throws Exception {
        Long siteId = createSite("SITE-CONN1", "연결감시센터1");
        Long robotId = createRobot(siteId, "ROBOT-CONN1", "연결로봇1");
        setInService(robotId);
        makeStale(robotId);

        List<Long> offline = robotConnectionService.markStaleRobotsOffline();

        assertThat(offline).contains(robotId);
        assertThat(robotCurrentStateRepository.findById(robotId).orElseThrow().getConnectionState()).isEqualTo(ConnectionState.OFFLINE);
        assertThat(openCommLostAlarm(robotId)).isTrue();

        robotConnectionService.receiveTelemetry("ROBOT-CONN1", 77);

        var state = robotCurrentStateRepository.findById(robotId).orElseThrow();
        assertThat(state.getConnectionState()).isEqualTo(ConnectionState.ONLINE);
        assertThat(state.getBatteryPct()).isEqualTo(77);
        assertThat(openCommLostAlarm(robotId)).isFalse();
    }

    @Test
    void 시뮬레이터_하트비트를_받은_로봇과_운영중이_아닌_로봇은_OFFLINE으로_바뀌지_않는다() throws Exception {
        Long siteId = createSite("SITE-CONN2", "연결감시센터2");
        Long heartbeatRobotId = createRobot(siteId, "ROBOT-CONN2", "하트비트로봇");
        setInService(heartbeatRobotId);
        makeStale(heartbeatRobotId);
        Long maintenanceRobotId = createRobot(siteId, "ROBOT-CONN3", "정비로봇");
        setInService(maintenanceRobotId);
        mockMvc.perform(patch("/api/admin/robots/{robotId}/lifecycle-status", maintenanceRobotId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRobotLifecycleStatusRequest(RobotLifecycleStatus.MAINTENANCE))))
                .andExpect(status().isOk());
        makeStale(maintenanceRobotId);

        // 하트비트는 운영 중(IN_SERVICE) 로봇만 갱신하므로 정비 로봇은 계속 오래된 상태로 남는다 — 그래도 감시 대상이 아니다.
        robotConnectionService.heartbeatSimulatedRobots(Set.of());
        List<Long> offline = robotConnectionService.markStaleRobotsOffline();

        assertThat(offline).doesNotContain(heartbeatRobotId, maintenanceRobotId);
        assertThat(robotCurrentStateRepository.findById(heartbeatRobotId).orElseThrow().getConnectionState()).isEqualTo(ConnectionState.ONLINE);
        assertThat(robotCurrentStateRepository.findById(maintenanceRobotId).orElseThrow().getConnectionState()).isEqualTo(ConnectionState.ONLINE);
    }

    /**
     * 로컬 실행 중 발견한 회귀 케이스: 서버가 오래 꺼져 있다 뜨면 첫 감시가 첫 하트비트보다 먼저 돌아 시뮬레이터 로봇이
     * OFFLINE이 될 수 있다 — 다음 하트비트가 복구해야 한다. 단, 통신 두절 장애가 주입된 로봇은 되살리지 않는다.
     */
    @Test
    void 시뮬레이터_하트비트는_끊긴_로봇을_복구하지만_통신두절_장애가_주입된_로봇은_제외한다() throws Exception {
        Long siteId = createSite("SITE-CONN4", "연결감시센터4");
        Long restartedRobotId = createRobot(siteId, "ROBOT-CONN4", "재기동로봇");
        Long faultedRobotId = createRobot(siteId, "ROBOT-CONN5", "장애로봇");
        setInService(restartedRobotId);
        setInService(faultedRobotId);
        makeStale(restartedRobotId);
        makeStale(faultedRobotId);
        assertThat(robotConnectionService.markStaleRobotsOffline()).contains(restartedRobotId, faultedRobotId);

        robotConnectionService.heartbeatSimulatedRobots(Set.of(faultedRobotId));

        assertThat(robotCurrentStateRepository.findById(restartedRobotId).orElseThrow().getConnectionState()).isEqualTo(ConnectionState.ONLINE);
        assertThat(openCommLostAlarm(restartedRobotId)).isFalse();
        assertThat(robotCurrentStateRepository.findById(faultedRobotId).orElseThrow().getConnectionState()).isEqualTo(ConnectionState.OFFLINE);
        assertThat(openCommLostAlarm(faultedRobotId)).isTrue();
    }

    private boolean openCommLostAlarm(Long robotId) {
        return alarmRepository.findFirstByAlarmTypeAndSourceTypeAndSourceIdAndStatusIn(
                AlarmType.ROBOT_COMM_LOST, AlarmSourceType.ROBOT, robotId, List.of(AlarmStatus.OPEN, AlarmStatus.ACKNOWLEDGED)
        ).isPresent();
    }

    /**
     * 마지막 수신 시각을 하트비트 타임아웃(기본 30초)보다 훨씬 전으로 돌린다. 저장된 값에서 DB가 직접 빼게 한다 —
     * Java에서 만든 Timestamp를 넣으면 JVM 시간대(예: KST)로 변환돼 Hibernate가 UTC로 저장한 값과 어긋난다.
     */
    private void makeStale(Long robotId) {
        jdbcTemplate.update(
                "update robot_current_states set received_at = date_sub(received_at, interval 10 minute) where robot_id = ?",
                robotId
        );
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

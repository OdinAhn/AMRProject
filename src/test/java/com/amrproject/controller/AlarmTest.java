package com.amrproject.controller;

import com.amrproject.domain.entity.alarm.Alarm;
import com.amrproject.domain.entity.alarm.AlarmSourceType;
import com.amrproject.domain.entity.alarm.AlarmStatus;
import com.amrproject.domain.entity.alarm.AlarmType;
import com.amrproject.domain.entity.robot.Robot;
import com.amrproject.domain.entity.site.StationType;
import com.amrproject.dto.request.robot.CreateRobotRequest;
import com.amrproject.dto.request.site.CreateSiteRequest;
import com.amrproject.dto.request.site.CreateStationRequest;
import com.amrproject.dto.request.task.CreateTaskRequest;
import com.amrproject.dto.request.robot.InjectFaultRequest;
import com.amrproject.dto.request.task.RecoverTaskRequest;
import com.amrproject.dto.request.task.RecoveryResolution;
import com.amrproject.repository.alarm.AlarmRepository;
import com.amrproject.repository.robot.RobotRepository;
import com.amrproject.robot.SimulatedFault;
import com.amrproject.service.TaskService;
import com.amrproject.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 14단계: 알람 발생/자동 해소(로봇 상태)와 확인·해제(작업 실패) 흐름을 검증한다.
 */
@AutoConfigureMockMvc
class AlarmTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private TaskService taskService;

    @Autowired
    private RobotRepository robotRepository;

    @Autowired
    private AlarmRepository alarmRepository;

    @Test
    void 로봇_통신이_두절되면_알람이_발생하고_재접속하면_자동_해소된다() throws Exception {
        Long siteId = createSite("SITE-ALM1", "알람 물류센터1");
        Long originId = createStation(siteId, "ST-ALM1", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-ALM2", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-ALM1", "알람로봇1");
        Long taskId = createTask(originId, destinationId, "idem-alm-1");

        // 신규 등록 로봇의 connection_state 기본값이 OFFLINE이라, 장애 없이 먼저
        // 한 번 정상 dispatch시켜 ONLINE으로 만들어야 이후 진짜 OFFLINE 전이를 관찰할 수 있다.
        Robot robot = robotRepository.findById(robotId).orElseThrow();
        taskService.assignRobot(taskId, robot);
        taskService.start(taskId);

        injectFault(robotId, SimulatedFault.COMMUNICATION_LOSS);

        mockMvc.perform(post("/api/worker/tasks/{taskId}/dispatch", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECOVERY_REQUIRED"));

        Alarm alarm = openAlarm(AlarmType.ROBOT_COMM_LOST, AlarmSourceType.ROBOT, robotId);
        assertThat(alarm.getStatus()).isEqualTo(AlarmStatus.OPEN);

        mockMvc.perform(post("/api/admin/tasks/{taskId}/recover", taskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecoverTaskRequest(RecoveryResolution.RESUME))))
                .andExpect(status().isOk());

        Alarm resolved = alarmRepository.findById(alarm.getId()).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(AlarmStatus.RESOLVED);
    }

    @Test
    void 위치추정_실패_장애를_주입하면_알람이_발생하고_다음_정상_이동에서_자동_해소된다() throws Exception {
        Long siteId = createSite("SITE-ALM2", "알람 물류센터2");
        Long originId = createStation(siteId, "ST-ALM3", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-ALM4", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-ALM2", "알람로봇2");
        Long taskId = createTask(originId, destinationId, "idem-alm-2");

        injectFault(robotId, SimulatedFault.LOCALIZATION_LOSS);

        Robot robot = robotRepository.findById(robotId).orElseThrow();
        taskService.assignRobot(taskId, robot);
        taskService.start(taskId);

        Alarm alarm = openAlarm(AlarmType.LOCALIZATION_LOST, AlarmSourceType.ROBOT, robotId);
        assertThat(alarm.getStatus()).isEqualTo(AlarmStatus.OPEN);

        mockMvc.perform(post("/api/worker/tasks/{taskId}/dispatch", taskId))
                .andExpect(status().isOk());

        Alarm resolved = alarmRepository.findById(alarm.getId()).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(AlarmStatus.RESOLVED);
    }

    @Test
    void 적재_센서_불일치_장애를_주입하면_알람이_발생하고_다음_정상_이동에서_자동_해소된다() throws Exception {
        Long siteId = createSite("SITE-ALM3", "알람 물류센터3");
        Long originId = createStation(siteId, "ST-ALM5", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-ALM6", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-ALM3", "알람로봇3");
        Long taskId = createTask(originId, destinationId, "idem-alm-3");

        injectFault(robotId, SimulatedFault.LOAD_SENSOR_MISMATCH);

        Robot robot = robotRepository.findById(robotId).orElseThrow();
        taskService.assignRobot(taskId, robot);
        taskService.start(taskId);

        Alarm alarm = openAlarm(AlarmType.LOAD_EXCEPTION, AlarmSourceType.ROBOT, robotId);
        assertThat(alarm.getStatus()).isEqualTo(AlarmStatus.OPEN);

        mockMvc.perform(post("/api/worker/tasks/{taskId}/dispatch", taskId))
                .andExpect(status().isOk());

        Alarm resolved = alarmRepository.findById(alarm.getId()).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(AlarmStatus.RESOLVED);
    }

    @Test
    void 작업이_복구불가로_최종실패하면_알람이_발생하고_확인_해제할_수_있다() throws Exception {
        Long siteId = createSite("SITE-ALM4", "알람 물류센터4");
        Long originId = createStation(siteId, "ST-ALM7", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-ALM8", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-ALM4", "알람로봇4");
        Long taskId = createTask(originId, destinationId, "idem-alm-4");

        injectFault(robotId, SimulatedFault.COMMAND_FAILURE);

        Robot robot = robotRepository.findById(robotId).orElseThrow();
        taskService.assignRobot(taskId, robot);
        taskService.start(taskId);

        mockMvc.perform(post("/api/admin/tasks/{taskId}/recover", taskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecoverTaskRequest(RecoveryResolution.FAIL))))
                .andExpect(status().isOk());

        Alarm alarm = openAlarm(AlarmType.TASK_FAILED, AlarmSourceType.TASK, taskId);
        assertThat(alarm.getStatus()).isEqualTo(AlarmStatus.OPEN);

        mockMvc.perform(get("/api/admin/alarms/{alarmId}", alarm.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions[0]").value("ACKNOWLEDGE"))
                .andExpect(jsonPath("$.allowedActions[1]").value("RESOLVE"))
                .andExpect(jsonPath("$.allowedActions.length()").value(2));

        mockMvc.perform(post("/api/admin/alarms/{alarmId}/acknowledge", alarm.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.allowedActions[0]").value("RESOLVE"))
                .andExpect(jsonPath("$.allowedActions.length()").value(1));

        // 이미 ACKNOWLEDGED인 알람에 재요청하면 그대로 현재 상태를 반환한다 (멱등).
        mockMvc.perform(post("/api/admin/alarms/{alarmId}/acknowledge", alarm.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));

        mockMvc.perform(post("/api/admin/alarms/{alarmId}/resolve", alarm.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.allowedActions.length()").value(0));

        // 이미 RESOLVED인 알람에 재요청하면 그대로 현재 상태를 반환한다 (멱등).
        mockMvc.perform(post("/api/admin/alarms/{alarmId}/resolve", alarm.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        // RESOLVED된 알람은 다시 확인 처리를 할 수 없다.
        mockMvc.perform(post("/api/admin/alarms/{alarmId}/acknowledge", alarm.getId()))
                .andExpect(status().isConflict());
    }

    private Alarm openAlarm(AlarmType type, AlarmSourceType sourceType, Long sourceId) {
        return alarmRepository.findFirstByAlarmTypeAndSourceTypeAndSourceIdAndStatusIn(
                        type, sourceType, sourceId, List.of(AlarmStatus.OPEN, AlarmStatus.ACKNOWLEDGED))
                .orElseThrow(() -> new AssertionError("알람이 생성되지 않았습니다: " + type));
    }

    private void injectFault(Long robotId, SimulatedFault fault) throws Exception {
        mockMvc.perform(post("/api/admin/robots/{robotId}/simulator/faults", robotId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InjectFaultRequest(fault))))
                .andExpect(status().isNoContent());
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

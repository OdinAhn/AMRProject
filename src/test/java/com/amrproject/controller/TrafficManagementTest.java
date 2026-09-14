package com.amrproject.controller;

import com.amrproject.domain.entity.map.EdgeDirection;
import com.amrproject.domain.entity.map.NodeType;
import com.amrproject.domain.entity.robot.RobotLifecycleStatus;
import com.amrproject.domain.entity.site.StationType;
import com.amrproject.dto.request.map.CreateEdgeRequest;
import com.amrproject.dto.request.map.CreateMapRequest;
import com.amrproject.dto.request.map.CreateMapVersionRequest;
import com.amrproject.dto.request.map.CreateNodeRequest;
import com.amrproject.dto.request.robot.CreateRobotRequest;
import com.amrproject.dto.request.robot.UpdateRobotLifecycleStatusRequest;
import com.amrproject.dto.request.site.BindStationMapRequest;
import com.amrproject.dto.request.site.CreateSiteRequest;
import com.amrproject.dto.request.site.CreateStationRequest;
import com.amrproject.dto.request.task.AssignRobotRequest;
import com.amrproject.dto.request.task.CreateTaskRequest;
import com.amrproject.service.robot.RobotStateReportService;
import com.amrproject.service.task.TaskService;
import com.amrproject.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 46단계: 교통 관리(경로 노드 예약). 지도는 일직선 N1(0) — N2(100) — N3(200) — N4(300)이고 입고 작업장은 N3, 출고는 N4다.
 * 이동하는 로봇 B는 N1에서 출발해 N2를 지나 N3로 간다.
 *
 * <p>재시도 스케줄러가 끼어들지 않게 주기를 길게 하고, 테스트가 {@link TaskService#retryWaitingCommand}를 직접 불러 상태를 하나씩 확인한다.
 * 교통 대기 알람은 바로 확인하도록 기준 시간을 0초로 둔다. 시뮬레이터라 보낸 이동은 즉시 끝난다.
 * 47단계: 스케줄러의 비키기도 끈다(테스트가 {@link TaskService#yieldForWaitingCommand}를 직접 부른다). 다만 경로 예약이 풀리면 커밋 뒤
 * 재시도가 바로 돌 수 있어, 예약을 푸는 동작 뒤의 재시도는 {@link #retryOrAlreadySent}로 확인한다.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {"app.traffic.retry-ms=3600000", "app.traffic.blocked-alarm-seconds=0", "app.traffic.yield-enabled=false"})
class TrafficManagementTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TaskService taskService;

    @Autowired
    private RobotStateReportService robotStateReportService;

    @Test
    void 다른_로봇이_서_있는_노드를_지나는_이동은_기다리고_비키면_경로를_예약해_출발한다() throws Exception {
        Fixture f = fixture("TR1");
        Long parked = createRobot(f.siteId, "ROBOT-TR1-A", "서있는로봇", false);
        placeRobot(parked, f.n2);

        Long taskId = createTask(f.originId, f.destinationId, "idem-tr-1");
        assign(taskId, f.mover)
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.steps[0].stepType").value("MOVE_TO_ORIGIN"))
                .andExpect(jsonPath("$.steps[0].status").value("RUNNING"));
        Long commandId = latestMoveCommandId(taskId);
        assertThat(commandStatus(commandId)).isEqualTo("PENDING");
        assertThat(lastTaskEvent(taskId)).isEqualTo("TRAFFIC_WAITING");
        assertThat(heldNodeReservations(f.mover)).isZero();

        // 아직 막혀 있다 — 기준 시간(0초)을 넘겨 알람이 뜬다
        assertThat(taskService.retryWaitingCommand(commandId)).isFalse();
        assertThat(alarmStatus(f.mover)).isEqualTo("OPEN");

        // 47단계: 운영 중이 아닌(점검) 로봇은 서버가 비키게 하지 않는다
        assertThat(taskService.findYieldCandidates(commandId)).extracting(TaskService.YieldCandidate::robotId).containsExactly(parked);
        assertThat(taskService.yieldForWaitingCommand(commandId, taskService.findYieldCandidates(commandId).getFirst())).isFalse();

        // 서 있던 로봇이 지도 밖으로 비킨다
        placeRobot(parked, null);
        assertThat(taskService.retryWaitingCommand(commandId)).isTrue();

        assertThat(commandStatus(commandId)).isEqualTo("SUCCEEDED");
        assertThat(jdbcTemplate.queryForObject("select payload_json from robot_commands where id = ?", String.class, commandId))
                .isEqualTo("{\"route\":[\"TR1-N1\",\"TR1-N2\",\"TR1-N3\"]}");
        mockMvc.perform(get("/api/admin/tasks/{taskId}", taskId))
                .andExpect(jsonPath("$.steps[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.steps[1].stepType").value("CONFIRM_LOAD"))
                .andExpect(jsonPath("$.steps[1].status").value("RUNNING"));
        assertThat(lastTaskEvent(taskId)).isEqualTo("TRAFFIC_CLEARED");
        assertThat(alarmStatus(f.mover)).isEqualTo("RESOLVED");
        // 도착했으니 경로 예약은 풀리고, 로봇은 현재 노드(N3)로 자리를 막는다
        assertThat(heldNodeReservations(f.mover)).isZero();
        assertThat(jdbcTemplate.queryForObject("select current_node_id from robot_current_states where robot_id = ?", Long.class, f.mover))
                .isEqualTo(f.n3);
    }

    @Test
    void 다른_로봇의_이동_경로로_예약된_노드가_있으면_예약이_풀릴_때까지_기다린다() throws Exception {
        Fixture f = fixture("TR2");
        Long crossing = createRobot(f.siteId, "ROBOT-TR2-C", "지나가는로봇", false);
        jdbcTemplate.update("insert into resource_reservations (resource_type, resource_id, robot_id, status, reserved_at) values ('NODE', ?, ?, 'HELD', now(6))",
                f.n2, crossing);

        Long taskId = createTask(f.originId, f.destinationId, "idem-tr-2");
        assign(taskId, f.mover).andExpect(jsonPath("$.steps[0].status").value("RUNNING"));
        Long commandId = latestMoveCommandId(taskId);
        assertThat(commandStatus(commandId)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("select payload from task_events where task_id = ? and event_type = 'TRAFFIC_WAITING'", String.class, taskId))
                .contains("ROBOT-TR2-C").contains("TR2-N2");

        jdbcTemplate.update("update resource_reservations set status = 'RELEASED', released_at = now(6) where robot_id = ?", crossing);
        assertThat(taskService.retryWaitingCommand(commandId)).isTrue();
        assertThat(commandStatus(commandId)).isEqualTo("SUCCEEDED");
    }

    @Test
    void 교통_대기_중_취소하면_보내지_않은_명령을_끝내고_알람을_해소한다() throws Exception {
        Fixture f = fixture("TR3");
        Long parked = createRobot(f.siteId, "ROBOT-TR3-A", "서있는로봇", false);
        placeRobot(parked, f.n2);
        Long taskId = createTask(f.originId, f.destinationId, "idem-tr-3");
        assign(taskId, f.mover).andExpect(status().isOk());
        Long commandId = latestMoveCommandId(taskId);
        taskService.retryWaitingCommand(commandId);
        assertThat(alarmStatus(f.mover)).isEqualTo("OPEN");

        mockMvc.perform(post("/api/admin/tasks/{taskId}/cancel", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        assertThat(commandStatus(commandId)).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForList("select event_type from command_events where command_id = ? order by id", String.class, commandId))
                .containsExactly("WAITING_FOR_TRAFFIC", "CANCELED");
        assertThat(alarmStatus(f.mover)).isEqualTo("RESOLVED");
        assertThat(jdbcTemplate.queryForObject("select count(*) from robot_commands where robot_id = ? and command_type = 'CANCEL'", Integer.class, f.mover))
                .isZero();
        assertThat(taskService.retryWaitingCommand(commandId)).isFalse();
    }

    @Test
    void 막는_로봇이_없으면_기다리지_않고_경로를_담아_바로_보낸다() throws Exception {
        Fixture f = fixture("TR4");
        Long taskId = createTask(f.originId, f.destinationId, "idem-tr-4");
        assign(taskId, f.mover)
                .andExpect(jsonPath("$.steps[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.steps[1].status").value("RUNNING"));
        assertThat(jdbcTemplate.queryForObject("select payload_json from robot_commands where id = ?", String.class, latestMoveCommandId(taskId)))
                .contains("TR4-N1").contains("TR4-N3");
        assertThat(jdbcTemplate.queryForList("select event_type from task_events where task_id = ?", String.class, taskId))
                .doesNotContain("TRAFFIC_WAITING");
    }

    /**
     * 사각형 지도 N1(0,0) — N2(100,0) — N3(100,100), N1 — N4(0,100) — N3. 최단 경로가 둘(N2 경유·N4 경유)이고, N2에 다른 로봇이
     * 서 있으면 기다리지 않고 N4로 돌아가는 경로를 예약해 바로 보낸다(격자 지도에서 멈춘 로봇 하나 때문에 끝없이 기다리던 문제).
     */
    @Test
    void 막힌_노드를_피해_돌아갈_길이_있으면_기다리지_않고_돌아간다() throws Exception {
        Long siteId = idOf(mockMvc.perform(post("/api/admin/sites").contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateSiteRequest("SITE-TR5", "교통센터 TR5")))));
        Long originId = idOf(mockMvc.perform(post("/api/admin/sites/{siteId}/stations", siteId).contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateStationRequest("ST-TR5-IN", "입고", StationType.PICKUP)))));
        Long destinationId = idOf(mockMvc.perform(post("/api/admin/sites/{siteId}/stations", siteId).contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateStationRequest("ST-TR5-OUT", "출고", StationType.DROPOFF)))));
        Long mapId = idOf(mockMvc.perform(post("/api/admin/sites/{siteId}/maps", siteId).contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateMapRequest("사각 지도")))));
        Long versionId = idOf(mockMvc.perform(post("/api/admin/maps/{mapId}/versions", mapId).contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateMapVersionRequest(null, "LOCAL_XY")))));
        Long n1 = node(versionId, "TR5-N1", 0, 0);
        Long n2 = node(versionId, "TR5-N2", 100, 0);
        Long n3 = node(versionId, "TR5-N3", 100, 100);
        Long n4 = node(versionId, "TR5-N4", 0, 100);
        edge(versionId, n1, n2);
        edge(versionId, n2, n3);
        edge(versionId, n1, n4);
        edge(versionId, n4, n3);
        mockMvc.perform(post("/api/admin/map-versions/{versionId}/validate", versionId)).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/map-versions/{versionId}/activate", versionId)).andExpect(status().isOk());
        bind(originId, versionId, n3);
        bind(destinationId, versionId, n1);
        Long mover = createRobot(siteId, "ROBOT-TR5-B", "이동로봇", true);
        placeRobot(mover, n1);
        Long parked = createRobot(siteId, "ROBOT-TR5-A", "서있는로봇", false);
        placeRobot(parked, n2);

        Long taskId = createTask(originId, destinationId, "idem-tr-5");
        assign(taskId, mover).andExpect(jsonPath("$.steps[0].status").value("SUCCEEDED"));
        assertThat(jdbcTemplate.queryForObject("select payload_json from robot_commands where id = ?", String.class, latestMoveCommandId(taskId)))
                .isEqualTo("{\"route\":[\"TR5-N1\",\"TR5-N4\",\"TR5-N3\"]}");
        assertThat(jdbcTemplate.queryForList("select event_type from task_events where task_id = ?", String.class, taskId))
                .doesNotContain("TRAFFIC_WAITING");
    }

    /**
     * 47단계: 일직선 N1 — N2 — N3 — N4에 N2에서 갈라지는 곁가지 S(가까움, 작업장 없음)와 W(더 멂, 대기 작업장)가 있다. 할 일 없이 N2에 서 있는
     * 운영 중 로봇 A가 N1에서 N3로 가려는 B의 길을 막으면, A를 길 밖으로 비키게 한다 — 곁가지 중 대기 작업장 노드 W를 먼저 고른다.
     * 시뮬레이터라 비키는 이동은 바로 끝나고 예약이 풀린 뒤 B가 출발한다.
     */
    @Test
    void 할_일_없는_로봇이_길을_막으면_대기_작업장_노드로_비키게_하고_출발한다() throws Exception {
        Long[] extra = new Long[2];
        Fixture f = fixture("TR6", (versionId, n1, n2) -> {
            extra[0] = node(versionId, "TR6-S", 100, 100);
            extra[1] = node(versionId, "TR6-W", 100, -200);
            edge(versionId, n2, extra[0]);
            edge(versionId, n2, extra[1]);
        });
        Long waiting = extra[1];
        Long waitingStation = idOf(mockMvc.perform(post("/api/admin/sites/{siteId}/stations", f.siteId).contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateStationRequest("ST-TR6-WAIT", "대기", StationType.WAITING)))));
        bind(waitingStation, activeVersionId(f.siteId), waiting);
        Long idle = createRobot(f.siteId, "ROBOT-TR6-A", "쉬는로봇", true);
        placeRobot(idle, f.n2);

        Long taskId = createTask(f.originId, f.destinationId, "idem-tr-6");
        assign(taskId, f.mover).andExpect(jsonPath("$.steps[0].status").value("RUNNING"));
        Long commandId = latestMoveCommandId(taskId);
        assertThat(commandStatus(commandId)).isEqualTo("PENDING");

        List<TaskService.YieldCandidate> candidates = taskService.findYieldCandidates(commandId);
        assertThat(candidates).extracting(TaskService.YieldCandidate::robotId).containsExactly(idle);
        assertThat(taskService.yieldForWaitingCommand(commandId, candidates.getFirst())).isTrue();

        assertThat(jdbcTemplate.queryForMap("select status, payload_json, task_step_id from robot_commands where robot_id = ? and command_type = 'REPOSITION'", idle))
                .containsEntry("status", "SUCCEEDED")
                .containsEntry("payload_json", "{\"route\":[\"TR6-N2\",\"TR6-W\"]}")
                .containsEntry("task_step_id", null);
        assertThat(currentNode(idle)).isEqualTo(waiting);
        assertThat(heldNodeReservations(idle)).isZero();
        assertThat(jdbcTemplate.queryForObject("select payload from task_events where task_id = ? and event_type = 'TRAFFIC_YIELD_REQUESTED'", String.class, taskId))
                .contains("ROBOT-TR6-A").contains("TR6-W");

        assertThat(retryOrAlreadySent(commandId)).isTrue();
        assertThat(commandStatus(commandId)).isEqualTo("SUCCEEDED");
        assertThat(currentNode(f.mover)).isEqualTo(f.n3);
    }

    /**
     * 47단계: 교착 — P(N1)는 N3 작업장으로, Q(N3)는 N1 작업장으로 가야 해 서로의 자리를 기다린다. 늦게 기다리기 시작한 Q가 곁가지 S로 비켜
     * P가 먼저 지나가고, 그 뒤 Q도 출발한다. 먼저 기다린 P는 Q를 위해 비키지 않는다(서로 번갈아 비키지 않게).
     */
    @Test
    void 서로의_자리를_기다리는_교착은_늦게_기다린_로봇이_비켜_풀린다() throws Exception {
        Long[] extra = new Long[1];
        Fixture f = fixture("TR7", (versionId, n1, n2) -> {
            extra[0] = node(versionId, "TR7-S", 100, 100);
            edge(versionId, n2, extra[0]);
        });
        Long versionId = activeVersionId(f.siteId);
        Long side = extra[0];
        Long westStation = idOf(mockMvc.perform(post("/api/admin/sites/{siteId}/stations", f.siteId).contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateStationRequest("ST-TR7-WEST", "서쪽", StationType.PICKUP)))));
        bind(westStation, versionId, f.n1);
        Long other = createRobot(f.siteId, "ROBOT-TR7-Q", "맞은편로봇", true);
        placeRobot(other, f.n3);

        Long eastTask = createTask(f.originId, f.destinationId, "idem-tr-7-p"); // P: N1 → N3(입고)
        assign(eastTask, f.mover).andExpect(jsonPath("$.steps[0].status").value("RUNNING"));
        Long westTask = createTask(westStation, f.destinationId, "idem-tr-7-q"); // Q: N3 → N1(서쪽)
        assign(westTask, other).andExpect(jsonPath("$.steps[0].status").value("RUNNING"));
        Long eastCommand = latestMoveCommandId(eastTask);
        Long westCommand = latestMoveCommandId(westTask);
        assertThat(commandStatus(eastCommand)).isEqualTo("PENDING");
        assertThat(commandStatus(westCommand)).isEqualTo("PENDING");

        // 먼저 기다린 P는 비키지 않는다
        List<TaskService.YieldCandidate> forWest = taskService.findYieldCandidates(westCommand);
        assertThat(forWest).extracting(TaskService.YieldCandidate::robotId).containsExactly(f.mover);
        assertThat(taskService.yieldForWaitingCommand(westCommand, forWest.getFirst())).isFalse();

        List<TaskService.YieldCandidate> forEast = taskService.findYieldCandidates(eastCommand);
        assertThat(forEast).extracting(TaskService.YieldCandidate::robotTaskId).containsExactly(westTask);
        assertThat(taskService.yieldForWaitingCommand(eastCommand, forEast.getFirst())).isTrue();
        assertThat(currentNode(other)).isEqualTo(side);

        assertThat(retryOrAlreadySent(eastCommand)).isTrue();
        assertThat(currentNode(f.mover)).isEqualTo(f.n3);
        assertThat(retryOrAlreadySent(westCommand)).isTrue();
        assertThat(jdbcTemplate.queryForObject("select payload_json from robot_commands where id = ?", String.class, westCommand))
                .isEqualTo("{\"route\":[\"TR7-S\",\"TR7-N2\",\"TR7-N1\"]}");
        assertThat(currentNode(other)).isEqualTo(f.n1);
    }

    /** 47단계: 로봇이 경로의 다음 노드에 왔다고 보고하면(MQTT lastNodeId) 지나온 노드 예약만 풀린다. */
    @Test
    void 경로의_노드에_도착했다고_보고하면_지나온_노드_예약을_푼다() throws Exception {
        Fixture f = fixture("TR8");
        for (Long nodeId : List.of(f.n1, f.n2, f.n3)) {
            jdbcTemplate.update("insert into resource_reservations (resource_type, resource_id, robot_id, status, reserved_at) values ('NODE', ?, ?, 'HELD', now(6))",
                    nodeId, f.mover);
        }

        robotStateReportService.apply(new RobotStateReportService.Report("ROBOT-TR8-B", null, null, "TR8-N2", List.of(), null, null));
        assertThat(heldNodes(f.mover)).containsExactly(f.n2, f.n3);

        robotStateReportService.apply(new RobotStateReportService.Report("ROBOT-TR8-B", null, null, "TR8-N3", List.of(), null, null));
        assertThat(heldNodes(f.mover)).containsExactly(f.n3);
    }

    /** 재시도해 보냈거나, 예약이 풀린 커밋 뒤 이벤트 재시도가 먼저 보냈다(작업·명령 행 잠금으로 둘 중 하나만 보낸다). */
    private boolean retryOrAlreadySent(Long commandId) {
        return taskService.retryWaitingCommand(commandId) || !commandStatus(commandId).equals("PENDING");
    }

    private Long activeVersionId(Long siteId) {
        return jdbcTemplate.queryForObject("select v.id from map_versions v join maps m on m.id = v.map_id where m.site_id = ? and v.status = 'ACTIVE'",
                Long.class, siteId);
    }

    private Long currentNode(Long robotId) {
        return jdbcTemplate.queryForObject("select current_node_id from robot_current_states where robot_id = ?", Long.class, robotId);
    }

    private List<Long> heldNodes(Long robotId) {
        return jdbcTemplate.queryForList("select resource_id from resource_reservations where robot_id = ? and resource_type = 'NODE' and status = 'HELD' order by id",
                Long.class, robotId);
    }

    private record Fixture(Long siteId, Long originId, Long destinationId, Long mover, Long n1, Long n2, Long n3, Long n4) {
    }

    /** 활성화 전 지도에 노드·통로를 더한다(활성 버전은 고칠 수 없다). */
    @FunctionalInterface
    private interface MapExtension {
        void extend(Long versionId, Long n1, Long n2) throws Exception;
    }

    private Fixture fixture(String prefix) throws Exception {
        return fixture(prefix, (versionId, n1, n2) -> {
        });
    }

    private Fixture fixture(String prefix, MapExtension extension) throws Exception {
        Long siteId = idOf(mockMvc.perform(post("/api/admin/sites").contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateSiteRequest("SITE-" + prefix, "교통센터 " + prefix)))));
        Long originId = idOf(mockMvc.perform(post("/api/admin/sites/{siteId}/stations", siteId).contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateStationRequest("ST-" + prefix + "-IN", "입고", StationType.PICKUP)))));
        Long destinationId = idOf(mockMvc.perform(post("/api/admin/sites/{siteId}/stations", siteId).contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateStationRequest("ST-" + prefix + "-OUT", "출고", StationType.DROPOFF)))));
        Long mapId = idOf(mockMvc.perform(post("/api/admin/sites/{siteId}/maps", siteId).contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateMapRequest("교통 지도")))));
        Long versionId = idOf(mockMvc.perform(post("/api/admin/maps/{mapId}/versions", mapId).contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateMapVersionRequest(null, "LOCAL_XY")))));
        Long n1 = node(versionId, prefix + "-N1", 0);
        Long n2 = node(versionId, prefix + "-N2", 100);
        Long n3 = node(versionId, prefix + "-N3", 200);
        Long n4 = node(versionId, prefix + "-N4", 300);
        edge(versionId, n1, n2);
        edge(versionId, n2, n3);
        edge(versionId, n3, n4);
        extension.extend(versionId, n1, n2);
        mockMvc.perform(post("/api/admin/map-versions/{versionId}/validate", versionId)).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/map-versions/{versionId}/activate", versionId)).andExpect(status().isOk());
        bind(originId, versionId, n3);
        bind(destinationId, versionId, n4);
        Long mover = createRobot(siteId, "ROBOT-" + prefix + "-B", "이동로봇", true);
        placeRobot(mover, n1);
        return new Fixture(siteId, originId, destinationId, mover, n1, n2, n3, n4);
    }

    private Long createRobot(Long siteId, String code, String name, boolean inService) throws Exception {
        Long robotId = idOf(mockMvc.perform(post("/api/admin/robots").contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateRobotRequest(code, name, null, null, siteId)))));
        // 운영 중이 아닌 로봇도 상태 행(위치)이 있어야 하므로 한 번 운영 전환해 상태를 만든 뒤 필요하면 되돌린다
        mockMvc.perform(patch("/api/admin/robots/{robotId}/lifecycle-status", robotId).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRobotLifecycleStatusRequest(RobotLifecycleStatus.IN_SERVICE))))
                .andExpect(status().isOk());
        if (!inService) {
            mockMvc.perform(patch("/api/admin/robots/{robotId}/lifecycle-status", robotId).contentType(APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateRobotLifecycleStatusRequest(RobotLifecycleStatus.MAINTENANCE))))
                    .andExpect(status().isOk());
        }
        return robotId;
    }

    private void placeRobot(Long robotId, Long nodeId) {
        jdbcTemplate.update("update robot_current_states set current_node_id = ? where robot_id = ?", nodeId, robotId);
    }

    private ResultActions assign(Long taskId, Long robotId) throws Exception {
        return mockMvc.perform(post("/api/admin/tasks/{taskId}/assign", taskId).contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AssignRobotRequest(robotId))));
    }

    private Long createTask(Long originId, Long destinationId, String idempotencyKey) throws Exception {
        return idOf(mockMvc.perform(post("/api/worker/tasks").header("Idempotency-Key", idempotencyKey).contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateTaskRequest(originId, destinationId)))));
    }

    private Long latestMoveCommandId(Long taskId) {
        return jdbcTemplate.queryForObject("select c.id from robot_commands c join task_steps s on s.id = c.task_step_id "
                + "where s.task_id = ? and c.command_type = 'MOVE_TO' order by c.id desc limit 1", Long.class, taskId);
    }

    private String commandStatus(Long commandId) {
        return jdbcTemplate.queryForObject("select status from robot_commands where id = ?", String.class, commandId);
    }

    private String lastTaskEvent(Long taskId) {
        return jdbcTemplate.queryForObject("select event_type from task_events where task_id = ? order by id desc limit 1", String.class, taskId);
    }

    private int heldNodeReservations(Long robotId) {
        return jdbcTemplate.queryForObject("select count(*) from resource_reservations where robot_id = ? and resource_type = 'NODE' and status = 'HELD'",
                Integer.class, robotId);
    }

    private String alarmStatus(Long robotId) {
        return jdbcTemplate.queryForObject("select status from alarms where alarm_type = 'TRAFFIC_BLOCKED' and source_type = 'ROBOT' and source_id = ? "
                + "order by id desc limit 1", String.class, robotId);
    }

    private Long node(Long versionId, String code, int x) throws Exception {
        return node(versionId, code, x, 0);
    }

    private Long node(Long versionId, String code, int x, int y) throws Exception {
        return idOf(mockMvc.perform(post("/api/admin/map-versions/{versionId}/nodes", versionId).contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateNodeRequest(code, BigDecimal.valueOf(x), BigDecimal.valueOf(y), NodeType.WAYPOINT)))));
    }

    private void edge(Long versionId, Long from, Long to) throws Exception {
        mockMvc.perform(post("/api/admin/map-versions/{versionId}/edges", versionId).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateEdgeRequest(from, to, EdgeDirection.TWO_WAY, null, null))))
                .andExpect(status().isCreated());
    }

    private void bind(Long stationId, Long versionId, Long nodeId) throws Exception {
        mockMvc.perform(put("/api/admin/stations/{stationId}/map-binding", stationId).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BindStationMapRequest(versionId, nodeId))))
                .andExpect(status().isOk());
    }

    private Long idOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
}

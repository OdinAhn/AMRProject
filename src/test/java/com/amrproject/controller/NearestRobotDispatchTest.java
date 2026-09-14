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
import com.amrproject.dto.request.task.CreateTaskRequest;
import com.amrproject.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 38단계: 디스패치가 출발 작업장까지 통로 거리가 가장 가까운 로봇을 고른다 — 우선순위 높은 작업이 먼저 고르고, 길이 없거나 위치를 모르는
 * 로봇은 뒤로 밀리며, 지도가 없으면 예전처럼 등록 순서다.
 *
 * <p>지도는 x축 위 일직선 N1(0) — N2(100) — N3(200)이고 입고 작업장은 N1, 출고 작업장은 N3에 연결한다. 로봇 위치는 이동을 흉내 내는 대신
 * 현재 노드를 직접 넣는다. 로봇은 모두 등록 순서(id)와 거리 순서가 반대가 되게 만들어, 예전 로직(id 순)이면 실패한다.
 */
@AutoConfigureMockMvc
class NearestRobotDispatchTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 가까운_로봇을_고르고_우선순위_높은_작업이_먼저_가져간다() throws Exception {
        Long siteId = createSite("SITE-NEAR1", "최근접센터1");
        Long originId = createStation(siteId, "ST-NEAR1", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-NEAR2", "출고", StationType.DROPOFF);
        Long[] nodes = activateLineMap(siteId, EdgeDirection.TWO_WAY, originId, destinationId);

        Long farRobot = createInServiceRobot(siteId, "ROBOT-NEAR1", "먼로봇");      // N3, 거리 200
        Long nearRobot = createInServiceRobot(siteId, "ROBOT-NEAR2", "가까운로봇");  // N2, 거리 100
        Long unknownRobot = createInServiceRobot(siteId, "ROBOT-NEAR3", "위치모름"); // 위치 모름
        placeRobot(farRobot, nodes[2]);
        placeRobot(nearRobot, nodes[1]);

        Long normalTask = createTask(originId, destinationId, 0, "idem-near-1");
        Long urgentTask = createTask(originId, destinationId, 9, "idem-near-2");

        mockMvc.perform(post("/api/admin/sites/{siteId}/dispatch", siteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments.length()").value(2))
                .andExpect(jsonPath("$.assignments[0].taskId").value(urgentTask))
                .andExpect(jsonPath("$.assignments[0].robotId").value(nearRobot))
                .andExpect(jsonPath("$.assignments[0].robotCode").value("ROBOT-NEAR2"))
                .andExpect(jsonPath("$.assignments[0].distanceToOrigin").value(100.0))
                .andExpect(jsonPath("$.assignments[1].taskId").value(normalTask))
                .andExpect(jsonPath("$.assignments[1].robotId").value(farRobot))
                .andExpect(jsonPath("$.assignments[1].distanceToOrigin").value(200.0));
        // 위치를 모르는 로봇은 거리를 아는 로봇이 남아 있는 동안 뽑히지 않는다
        assertThat(jdbcTemplate.queryForObject("select count(*) from task_assignments where robot_id = ?", Integer.class, unknownRobot)).isZero();
    }

    @Test
    void 출발지로_갈_길이_없는_로봇은_위치를_모르는_로봇처럼_뒤로_밀린다() throws Exception {
        Long siteId = createSite("SITE-NEAR2", "최근접센터2");
        Long originId = createStation(siteId, "ST-NEAR3", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-NEAR4", "출고", StationType.DROPOFF);
        // N1—N2는 양방향, N2→N3은 한 방향뿐이라 N3에서는 N1(입고)로 돌아갈 수 없다(N2에서는 갈 수 있다)
        Long[] nodes = activateLineMap(siteId, EdgeDirection.ONE_WAY, originId, destinationId);

        Long stuckRobot = createInServiceRobot(siteId, "ROBOT-NEAR4", "막힌로봇");     // N3, 길 없음
        Long unknownRobot = createInServiceRobot(siteId, "ROBOT-NEAR5", "위치모름");   // 위치 모름
        Long reachableRobot = createInServiceRobot(siteId, "ROBOT-NEAR6", "길있는로봇"); // N2, 거리 100
        placeRobot(stuckRobot, nodes[2]);
        placeRobot(reachableRobot, nodes[1]);

        Long firstTask = createTask(originId, destinationId, 0, "idem-near-3");
        Long secondTask = createTask(originId, destinationId, 0, "idem-near-4");

        mockMvc.perform(post("/api/admin/sites/{siteId}/dispatch", siteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments[0].taskId").value(firstTask))
                .andExpect(jsonPath("$.assignments[0].robotId").value(reachableRobot))
                .andExpect(jsonPath("$.assignments[0].distanceToOrigin").value(100.0))
                // 거리를 모르는 두 로봇 중에서는 등록 순서
                .andExpect(jsonPath("$.assignments[1].taskId").value(secondTask))
                .andExpect(jsonPath("$.assignments[1].robotId").value(stuckRobot))
                .andExpect(jsonPath("$.assignments[1].distanceToOrigin").doesNotExist());
    }

    @Test
    void 활성_지도가_없으면_등록_순서로_고르고_거리는_비어있다() throws Exception {
        Long siteId = createSite("SITE-NEAR3", "최근접센터3");
        Long originId = createStation(siteId, "ST-NEAR5", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-NEAR6", "출고", StationType.DROPOFF);
        Long firstRobot = createInServiceRobot(siteId, "ROBOT-NEAR7", "로봇7");
        createInServiceRobot(siteId, "ROBOT-NEAR8", "로봇8");
        Long taskId = createTask(originId, destinationId, 0, "idem-near-5");

        mockMvc.perform(post("/api/admin/sites/{siteId}/dispatch", siteId))
                .andExpect(jsonPath("$.assignments[0].taskId").value(taskId))
                .andExpect(jsonPath("$.assignments[0].robotId").value(firstRobot))
                .andExpect(jsonPath("$.assignments[0].distanceToOrigin").doesNotExist());
    }

    /** N1(0,0) —(양방향)— N2(100,0) —(secondEdge)— N3(200,0). 입고=N1, 출고=N3. 반환: [N1, N2, N3] 노드 id */
    private Long[] activateLineMap(Long siteId, EdgeDirection secondEdge, Long originId, Long destinationId) throws Exception {
        Long versionId = createVersion(createMap(siteId));
        Long n1 = createNode(versionId, "N1", 0);
        Long n2 = createNode(versionId, "N2", 100);
        Long n3 = createNode(versionId, "N3", 200);
        createEdge(versionId, n1, n2, EdgeDirection.TWO_WAY);
        createEdge(versionId, n2, n3, secondEdge);
        mockMvc.perform(post("/api/admin/map-versions/{versionId}/validate", versionId)).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/map-versions/{versionId}/activate", versionId)).andExpect(status().isOk());
        bind(originId, versionId, n1);
        bind(destinationId, versionId, n3);
        return new Long[]{n1, n2, n3};
    }

    private void placeRobot(Long robotId, Long nodeId) {
        jdbcTemplate.update("update robot_current_states set current_node_id = ? where robot_id = ?", nodeId, robotId);
    }

    private void bind(Long stationId, Long versionId, Long nodeId) throws Exception {
        mockMvc.perform(put("/api/admin/stations/{stationId}/map-binding", stationId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BindStationMapRequest(versionId, nodeId))))
                .andExpect(status().isOk());
    }

    private void createEdge(Long versionId, Long from, Long to, EdgeDirection direction) throws Exception {
        mockMvc.perform(post("/api/admin/map-versions/{versionId}/edges", versionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateEdgeRequest(from, to, direction, null, null))))
                .andExpect(status().isCreated());
    }

    private Long createNode(Long versionId, String code, int x) throws Exception {
        return idOf(mockMvc.perform(post("/api/admin/map-versions/{versionId}/nodes", versionId)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateNodeRequest(code, BigDecimal.valueOf(x), BigDecimal.ZERO, NodeType.STATION)))));
    }

    private Long createVersion(Long mapId) throws Exception {
        return idOf(mockMvc.perform(post("/api/admin/maps/{mapId}/versions", mapId)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateMapVersionRequest(null, "LOCAL_XY")))));
    }

    private Long createMap(Long siteId) throws Exception {
        return idOf(mockMvc.perform(post("/api/admin/sites/{siteId}/maps", siteId)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateMapRequest("최근접 지도")))));
    }

    private Long createInServiceRobot(Long siteId, String code, String name) throws Exception {
        Long robotId = idOf(mockMvc.perform(post("/api/admin/robots")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateRobotRequest(code, name, null, null, siteId)))));
        mockMvc.perform(patch("/api/admin/robots/{robotId}/lifecycle-status", robotId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRobotLifecycleStatusRequest(RobotLifecycleStatus.IN_SERVICE))))
                .andExpect(status().isOk());
        return robotId;
    }

    private Long createTask(Long originId, Long destinationId, int priority, String idempotencyKey) throws Exception {
        return idOf(mockMvc.perform(post("/api/worker/tasks")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateTaskRequest(originId, destinationId, priority)))));
    }

    private Long createSite(String code, String name) throws Exception {
        return idOf(mockMvc.perform(post("/api/admin/sites")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateSiteRequest(code, name)))));
    }

    private Long createStation(Long siteId, String code, String name, StationType stationType) throws Exception {
        return idOf(mockMvc.perform(post("/api/admin/sites/{siteId}/stations", siteId)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateStationRequest(code, name, stationType)))));
    }

    private Long idOf(ResultActions result) throws Exception {
        String response = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}

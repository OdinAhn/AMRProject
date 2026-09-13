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
import com.amrproject.repository.event.OutboxEventRepository;
import com.amrproject.service.map.MapRouteService;
import com.amrproject.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 30단계: 로봇 현재 노드 추적, 활성 지도 최단 경로, 대시보드 개요 API, robot.state_changed 이벤트를 검증한다.
 *
 * <p>지도: N1(10,10) — N2(90,10) — N3(90,90) 두 방향 통로와, 짧아 보이지만 N3→N1 한 방향뿐인 대각선 통로.
 * 입고 작업장은 N1, 출고 작업장은 N3에 연결한다.
 */
@AutoConfigureMockMvc
class SiteOverviewTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private MapRouteService mapRouteService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void 이동하면_로봇이_목적지_노드에_있고_대시보드_개요에_위치_작업_알람이_모인다() throws Exception {
        Long siteId = createSite("SITE-OVW1", "대시보드센터1");
        Long originId = createStation(siteId, "ST-OVW1", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-OVW2", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-OVW1", "대시보드로봇1");
        setInService(robotId);

        Long versionId = createVersion(createMap(siteId));
        Long n1 = createNode(versionId, "N1", 10, 10);
        Long n2 = createNode(versionId, "N2", 90, 10);
        Long n3 = createNode(versionId, "N3", 90, 90);
        createEdge(versionId, n1, n2, EdgeDirection.TWO_WAY);
        createEdge(versionId, n2, n3, EdgeDirection.TWO_WAY);
        createEdge(versionId, n3, n1, EdgeDirection.ONE_WAY);
        mockMvc.perform(post("/api/admin/map-versions/{versionId}/validate", versionId)).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/map-versions/{versionId}/activate", versionId)).andExpect(status().isOk());
        bind(originId, versionId, n1);
        bind(destinationId, versionId, n3);

        Long taskId = createTask(originId, destinationId, "idem-ovw-1");
        mockMvc.perform(post("/api/admin/tasks/{taskId}/assign", taskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignRobotRequest(robotId))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/robots/{robotId}/state", robotId))
                .andExpect(jsonPath("$.currentNodeId").value(n1));

        mockMvc.perform(post("/api/worker/tasks/{taskId}/dispatch", taskId)).andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/sites/{siteId}/overview", siteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeMapVersionId").value(versionId))
                .andExpect(jsonPath("$.robots[0].robotId").value(robotId))
                .andExpect(jsonPath("$.robots[0].connectionState").value("ONLINE"))
                .andExpect(jsonPath("$.robots[0].currentNodeId").value(n3))
                .andExpect(jsonPath("$.robots[0].currentTaskId").value(taskId))
                .andExpect(jsonPath("$.openTaskCounts.RUNNING").value(1))
                .andExpect(jsonPath("$.openTaskCounts.QUEUED").value(0))
                .andExpect(jsonPath("$.openAlarmCount").value(0));

        // 출고(N3)→입고(N1): 대각선 통로는 N3→N1 한 방향이라 쓸 수 있고, 반대 방향은 N1→N2→N3로 돌아간다.
        assertThat(mapRouteService.shortestPath(versionId, n3, n1)).extracting(MapRouteService.Waypoint::nodeId).containsExactly(n3, n1);
        assertThat(mapRouteService.shortestPath(versionId, n1, n3)).extracting(MapRouteService.Waypoint::nodeId).containsExactly(n1, n2, n3);

        assertThat(outboxEventRepository.findAll()).anyMatch(event ->
                "ROBOT".equals(event.getAggregateType()) && robotId.equals(event.getAggregateId())
                        && "robot.state_changed".equals(event.getEventType()));
    }

    @Test
    void 활성_지도가_없으면_위치를_모르는_채로_이동하고_개요의_지도_버전은_비어있다() throws Exception {
        Long siteId = createSite("SITE-OVW2", "대시보드센터2");
        Long originId = createStation(siteId, "ST-OVW3", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-OVW4", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-OVW2", "대시보드로봇2");
        setInService(robotId);
        Long taskId = createTask(originId, destinationId, "idem-ovw-2");
        mockMvc.perform(post("/api/admin/tasks/{taskId}/assign", taskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignRobotRequest(robotId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        mockMvc.perform(get("/api/admin/sites/{siteId}/overview", siteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeMapVersionId").doesNotExist())
                .andExpect(jsonPath("$.robots[0].currentNodeId").doesNotExist());
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

    private Long createNode(Long versionId, String code, int x, int y) throws Exception {
        String response = mockMvc.perform(post("/api/admin/map-versions/{versionId}/nodes", versionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateNodeRequest(code, BigDecimal.valueOf(x), BigDecimal.valueOf(y), NodeType.STATION))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private Long createVersion(Long mapId) throws Exception {
        String response = mockMvc.perform(post("/api/admin/maps/{mapId}/versions", mapId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateMapVersionRequest(null, "LOCAL_XY"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private Long createMap(Long siteId) throws Exception {
        String response = mockMvc.perform(post("/api/admin/sites/{siteId}/maps", siteId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateMapRequest("대시보드 지도"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
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

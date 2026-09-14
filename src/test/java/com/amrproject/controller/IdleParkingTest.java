package com.amrproject.controller;

import com.amrproject.domain.entity.map.EdgeDirection;
import com.amrproject.domain.entity.map.NodeType;
import com.amrproject.domain.entity.map.ZoneType;
import com.amrproject.domain.entity.robot.RobotLifecycleStatus;
import com.amrproject.dto.request.map.CreateEdgeRequest;
import com.amrproject.dto.request.map.CreateMapRequest;
import com.amrproject.dto.request.map.CreateMapVersionRequest;
import com.amrproject.dto.request.map.CreateNodeRequest;
import com.amrproject.dto.request.map.CreateZoneRequest;
import com.amrproject.dto.request.robot.CreateRobotRequest;
import com.amrproject.dto.request.robot.UpdateRobotLifecycleStatusRequest;
import com.amrproject.dto.request.site.CreateSiteRequest;
import com.amrproject.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 48단계: 할 일 없는 로봇을 대기 구역(PARKING)으로 고르게 보낸다. 지도는 허브 H(0,0)에 가지가 붙은 별 모양이다.
 * <ul>
 *   <li>서쪽 대기 구역 Z-W: W1(-100,0), W2(-100,100) — 둘 다 H와 연결, 수용량 2</li>
 *   <li>동쪽 대기 구역 Z-E: E1(150,0) — H와 연결, 수용량 1</li>
 *   <li>로봇 자리: S1(0,100)—H, S2(0,-100)—H, S3(0,-200)—S2</li>
 * </ul>
 * 시뮬레이터라 보낸 이동은 바로 끝나 다음 로봇 계획에 반영된다. 막 일을 마친 로봇을 기다리는 시간은 0초로 둔다.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {"app.parking.idle-seconds=0", "app.traffic.retry-ms=3600000"})
class IdleParkingTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * R1(S1)은 두 구역이 모두 비어 가까운 서쪽 W1로, R2(S2)는 서쪽이 절반 찼으니 빈 동쪽 E1으로, R3(S3)는 동쪽이 가득 차 서쪽 W2로 간다.
     * 다음 사이클에는 모두 대기 구역에 있어 아무도 움직이지 않는다.
     */
    @Test
    void 할_일_없는_로봇을_대기_구역에_고르게_보내고_이미_있으면_두지_않는다() throws Exception {
        Fixture f = fixture("PK1");
        Long r1 = createRobot(f.siteId, "ROBOT-PK1-1", f.s1);
        Long r2 = createRobot(f.siteId, "ROBOT-PK1-2", f.s2);
        Long r3 = createRobot(f.siteId, "ROBOT-PK1-3", f.s3);

        JsonNode result = dispatch(f.siteId);
        assertThat(ids(result.get("parkedRobotIds"))).containsExactly(r1, r2, r3);
        assertThat(currentNode(r1)).isEqualTo(f.w1);
        assertThat(currentNode(r2)).isEqualTo(f.e1);
        assertThat(currentNode(r3)).isEqualTo(f.w2);
        assertThat(jdbcTemplate.queryForList("select c.status from robot_commands c where c.robot_id in (?, ?, ?) and c.command_type = 'REPOSITION' "
                + "and c.task_step_id is null", String.class, r1, r2, r3)).containsExactly("SUCCEEDED", "SUCCEEDED", "SUCCEEDED");
        assertThat(jdbcTemplate.queryForList("select e.event_type from command_events e join robot_commands c on c.id = e.command_id where c.robot_id = ? order by e.id",
                String.class, r1)).startsWith("PARKING");
        assertThat(jdbcTemplate.queryForObject("select count(*) from resource_reservations where robot_id in (?, ?, ?) and status = 'HELD'",
                Integer.class, r1, r2, r3)).isZero();

        assertThat(ids(dispatch(f.siteId).get("parkedRobotIds"))).isEmpty();
    }

    /** 배터리가 충전 기준(기본 15%) 이하인 로봇은 대기 구역으로 보내지 않는다 — 충전 대상이다(이 물류센터엔 충전소가 없어 그대로 둔다). */
    @Test
    void 배터리가_충전_기준_이하인_로봇은_대기_구역으로_보내지_않는다() throws Exception {
        Fixture f = fixture("PK2");
        Long low = createRobot(f.siteId, "ROBOT-PK2-1", f.s1);
        jdbcTemplate.update("update robot_current_states set battery_pct = 10 where robot_id = ?", low);

        JsonNode result = dispatch(f.siteId);
        assertThat(ids(result.get("parkedRobotIds"))).isEmpty();
        assertThat(currentNode(low)).isEqualTo(f.s1);
    }

    private record Fixture(Long siteId, Long w1, Long w2, Long e1, Long s1, Long s2, Long s3) {
    }

    private Fixture fixture(String prefix) throws Exception {
        Long siteId = idOf(mockMvc.perform(post("/api/admin/sites").contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateSiteRequest("SITE-" + prefix, "대기 구역 센터 " + prefix)))));
        Long mapId = idOf(mockMvc.perform(post("/api/admin/sites/{siteId}/maps", siteId).contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateMapRequest("별 지도")))));
        Long versionId = idOf(mockMvc.perform(post("/api/admin/maps/{mapId}/versions", mapId).contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateMapVersionRequest(null, "LOCAL_XY")))));
        Long hub = node(versionId, prefix + "-H", 0, 0);
        Long w1 = node(versionId, prefix + "-W1", -100, 0);
        Long w2 = node(versionId, prefix + "-W2", -100, 100);
        Long e1 = node(versionId, prefix + "-E1", 150, 0);
        Long s1 = node(versionId, prefix + "-S1", 0, 100);
        Long s2 = node(versionId, prefix + "-S2", 0, -100);
        Long s3 = node(versionId, prefix + "-S3", 0, -200);
        for (Long spoke : List.of(w1, w2, e1, s1, s2)) {
            edge(versionId, hub, spoke);
        }
        edge(versionId, s2, s3);
        zone(versionId, prefix + "-Z-W", List.of(w1, w2));
        zone(versionId, prefix + "-Z-E", List.of(e1));
        mockMvc.perform(post("/api/admin/map-versions/{versionId}/validate", versionId)).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/map-versions/{versionId}/activate", versionId)).andExpect(status().isOk());
        return new Fixture(siteId, w1, w2, e1, s1, s2, s3);
    }

    private Long createRobot(Long siteId, String code, Long nodeId) throws Exception {
        Long robotId = idOf(mockMvc.perform(post("/api/admin/robots").contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateRobotRequest(code, code, null, null, siteId)))));
        mockMvc.perform(patch("/api/admin/robots/{robotId}/lifecycle-status", robotId).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRobotLifecycleStatusRequest(RobotLifecycleStatus.IN_SERVICE))))
                .andExpect(status().isOk());
        jdbcTemplate.update("update robot_current_states set current_node_id = ? where robot_id = ?", nodeId, robotId);
        return robotId;
    }

    private JsonNode dispatch(Long siteId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/admin/sites/{siteId}/dispatch", siteId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private List<Long> ids(JsonNode array) {
        return array.valueStream().map(JsonNode::asLong).toList();
    }

    private Long currentNode(Long robotId) {
        return jdbcTemplate.queryForObject("select current_node_id from robot_current_states where robot_id = ?", Long.class, robotId);
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

    private void zone(Long versionId, String code, List<Long> nodeIds) throws Exception {
        mockMvc.perform(post("/api/admin/map-versions/{versionId}/zones", versionId).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateZoneRequest(code, ZoneType.PARKING, null, nodeIds))))
                .andExpect(status().isCreated());
    }

    private Long idOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
}

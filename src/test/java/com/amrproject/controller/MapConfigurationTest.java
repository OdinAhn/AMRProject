package com.amrproject.controller;

import com.amrproject.domain.entity.map.EdgeDirection;
import com.amrproject.domain.entity.map.NodeType;
import com.amrproject.domain.entity.site.StationType;
import com.amrproject.dto.request.site.BindStationMapRequest;
import com.amrproject.dto.request.map.CreateEdgeRequest;
import com.amrproject.dto.request.map.CreateMapRequest;
import com.amrproject.dto.request.map.CreateMapVersionRequest;
import com.amrproject.dto.request.map.CreateNodeRequest;
import com.amrproject.dto.request.map.CreateObstacleRequest;
import com.amrproject.dto.request.site.CreateSiteRequest;
import com.amrproject.dto.request.site.CreateStationRequest;
import com.amrproject.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 6단계 완료 기준(유효하지 않은 설정 차단, 적용 상태 추적) 중 지도 버전 부분을 검증한다.
 */
@AutoConfigureMockMvc
class MapConfigurationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Test
    void 노드_통로_추가_후_검증_활성화하면_이전_활성_버전이_보관된다() throws Exception {
        Long siteId = createSite("SITE-MAP1", "지도물류센터1");
        Long mapId = createMap(siteId, "1층 지도");
        Long versionId = createVersion(mapId);

        Long nodeAId = createNode(versionId, "N-A", NodeType.WAYPOINT);
        Long nodeBId = createNode(versionId, "N-B", NodeType.WAYPOINT);
        mockMvc.perform(post("/api/admin/map-versions/{versionId}/edges", versionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateEdgeRequest(nodeAId, nodeBId, EdgeDirection.TWO_WAY, BigDecimal.TEN, 1))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/map-versions/{versionId}/validate", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.mapVersion.status").value("VALIDATED"));

        mockMvc.perform(post("/api/admin/map-versions/{versionId}/activate", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        Long secondVersionId = createVersion(mapId);
        createNode(secondVersionId, "N-C", NodeType.WAYPOINT);
        mockMvc.perform(post("/api/admin/map-versions/{versionId}/validate", secondVersionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
        mockMvc.perform(post("/api/admin/map-versions/{versionId}/activate", secondVersionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/admin/map-versions/{versionId}", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void 노드가_없으면_검증이_실패하고_DRAFT로_남는다() throws Exception {
        Long siteId = createSite("SITE-MAP2", "지도물류센터2");
        Long mapId = createMap(siteId, "2층 지도");
        Long versionId = createVersion(mapId);

        mockMvc.perform(post("/api/admin/map-versions/{versionId}/validate", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.mapVersion.status").value("DRAFT"));
    }

    @Test
    void VALIDATED_버전에_노드를_추가하면_409를_반환한다() throws Exception {
        Long siteId = createSite("SITE-MAP3", "지도물류센터3");
        Long mapId = createMap(siteId, "3층 지도");
        Long versionId = createVersion(mapId);
        createNode(versionId, "N-X", NodeType.WAYPOINT);

        mockMvc.perform(post("/api/admin/map-versions/{versionId}/validate", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        mockMvc.perform(post("/api/admin/map-versions/{versionId}/nodes", versionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateNodeRequest("N-Y", BigDecimal.ONE, BigDecimal.ONE, NodeType.WAYPOINT))))
                .andExpect(status().isConflict());
    }

    @Test
    void station_map_binding은_PUT을_다시_호출하면_노드가_교체된다() throws Exception {
        Long siteId = createSite("SITE-MAP4", "지도물류센터4");
        Long stationId = createStation(siteId, "ST-M1", "적재장");
        Long mapId = createMap(siteId, "4층 지도");
        Long versionId = createVersion(mapId);
        Long nodeAId = createNode(versionId, "N-A", NodeType.STATION);
        Long nodeBId = createNode(versionId, "N-B", NodeType.STATION);
        mockMvc.perform(post("/api/admin/map-versions/{versionId}/validate", versionId)).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/map-versions/{versionId}/activate", versionId)).andExpect(status().isOk());

        mockMvc.perform(put("/api/admin/stations/{stationId}/map-binding", stationId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BindStationMapRequest(versionId, nodeAId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeId").value(nodeAId));

        mockMvc.perform(put("/api/admin/stations/{stationId}/map-binding", stationId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BindStationMapRequest(versionId, nodeBId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeId").value(nodeBId));
    }

    @Test
    void 통로가_장애물과_겹치면_검증이_실패한다() throws Exception {
        Long siteId = createSite("SITE-MAP5", "지도물류센터5");
        Long mapId = createMap(siteId, "5층 지도");
        Long versionId = createVersion(mapId);

        Long nodeAId = createNodeAt(versionId, "N-A", BigDecimal.ZERO, BigDecimal.ZERO);
        Long nodeBId = createNodeAt(versionId, "N-B", BigDecimal.TEN, BigDecimal.ZERO);
        mockMvc.perform(post("/api/admin/map-versions/{versionId}/edges", versionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateEdgeRequest(nodeAId, nodeBId, EdgeDirection.TWO_WAY, null, null))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/map-versions/{versionId}/obstacles", versionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateObstacleRequest(
                                BigDecimal.valueOf(4), BigDecimal.valueOf(-1), BigDecimal.valueOf(2), BigDecimal.valueOf(2)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.width").value(2));

        mockMvc.perform(post("/api/admin/map-versions/{versionId}/validate", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors.length()").value(1));
    }

    @Test
    void 통로가_장애물을_피해가면_검증이_통과한다() throws Exception {
        Long siteId = createSite("SITE-MAP6", "지도물류센터6");
        Long mapId = createMap(siteId, "6층 지도");
        Long versionId = createVersion(mapId);

        Long nodeAId = createNodeAt(versionId, "N-A", BigDecimal.ZERO, BigDecimal.ZERO);
        Long nodeBId = createNodeAt(versionId, "N-B", BigDecimal.TEN, BigDecimal.ZERO);
        mockMvc.perform(post("/api/admin/map-versions/{versionId}/edges", versionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateEdgeRequest(nodeAId, nodeBId, EdgeDirection.TWO_WAY, null, null))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/map-versions/{versionId}/obstacles", versionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateObstacleRequest(
                                BigDecimal.valueOf(4), BigDecimal.valueOf(5), BigDecimal.valueOf(2), BigDecimal.valueOf(2)))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/map-versions/{versionId}/validate", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    private Long createNodeAt(Long versionId, String code, BigDecimal x, BigDecimal y) throws Exception {
        String response = mockMvc.perform(post("/api/admin/map-versions/{versionId}/nodes", versionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateNodeRequest(code, x, y, NodeType.WAYPOINT))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private Long createNode(Long versionId, String code, NodeType nodeType) throws Exception {
        String response = mockMvc.perform(post("/api/admin/map-versions/{versionId}/nodes", versionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateNodeRequest(code, BigDecimal.ONE, BigDecimal.ONE, nodeType))))
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

    private Long createMap(Long siteId, String name) throws Exception {
        String response = mockMvc.perform(post("/api/admin/sites/{siteId}/maps", siteId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateMapRequest(name))))
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

    private Long createStation(Long siteId, String code, String name) throws Exception {
        String response = mockMvc.perform(post("/api/admin/sites/{siteId}/stations", siteId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateStationRequest(code, name, StationType.GENERIC))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}

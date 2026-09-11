package com.amrproject.controller;

import com.amrproject.domain.entity.StationType;
import com.amrproject.dto.request.CreateRobotRequest;
import com.amrproject.dto.request.CreateSiteRequest;
import com.amrproject.dto.request.CreateStationRequest;
import com.amrproject.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class StationAndRobotRegistrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Test
    void 작업장_2개와_로봇_1대를_등록하고_조회할_수_있다() throws Exception {
        Long siteId = createSite("SITE-01", "테스트 물류센터");

        createStation(siteId, "ST-01", "입고 작업장", StationType.PICKUP);
        createStation(siteId, "ST-02", "출고 작업장", StationType.DROPOFF);
        createRobot(siteId, "ROBOT-01", "1호기");

        mockMvc.perform(get("/api/admin/sites/{siteId}/stations", siteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/admin/robots").param("siteId", siteId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("ROBOT-01"));
    }

    private Long createSite(String code, String name) throws Exception {
        String response = mockMvc.perform(post("/api/admin/sites")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSiteRequest(code, name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void createStation(Long siteId, String code, String name, StationType stationType) throws Exception {
        mockMvc.perform(post("/api/admin/sites/{siteId}/stations", siteId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateStationRequest(code, name, stationType))))
                .andExpect(status().isCreated());
    }

    private void createRobot(Long siteId, String code, String name) throws Exception {
        mockMvc.perform(post("/api/admin/robots")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRobotRequest(code, name, null, null, siteId))))
                .andExpect(status().isCreated());
    }
}

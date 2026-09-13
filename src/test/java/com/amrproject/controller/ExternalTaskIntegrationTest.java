package com.amrproject.controller;

import com.amrproject.domain.entity.site.StationType;
import com.amrproject.dto.request.task.AssignRobotRequest;
import com.amrproject.dto.request.task.CreateExternalTaskRequest;
import com.amrproject.dto.request.robot.CreateRobotRequest;
import com.amrproject.dto.request.site.CreateSiteRequest;
import com.amrproject.dto.request.site.CreateStationRequest;
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
 * 9단계 완료 기준(외부 요청부터 결과 회신까지 추적) 중 구현 가능한 부분(최소
 * 접수/조회 계약)을 검증한다. 26단계부터 외부 API는 X-API-Key 헤더가 필요하다
 * (값은 application-test.yaml의 app.external.api-key).
 */
@AutoConfigureMockMvc
class ExternalTaskIntegrationTest extends AbstractIntegrationTest {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY = "test-only-external-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Test
    void API_키가_없거나_틀리면_401을_반환한다() throws Exception {
        Long siteId = createSite("SITE-EXT5", "외부연동5");
        Long originId = createStation(siteId, "ST-E9", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-E10", "출고", StationType.DROPOFF);
        String body = objectMapper.writeValueAsString(new CreateExternalTaskRequest(originId, destinationId, "WMS-ORDER-AUTH"));

        mockMvc.perform(post("/api/external/tasks").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/external/tasks").header(API_KEY_HEADER, "wrong-key").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/external/tasks/{externalRef}", "WMS-ORDER-AUTH"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 관리자_토큰만으로는_외부_API를_호출할_수_없다() throws Exception {
        // @AutoConfigureMockMvc 기본 요청에는 관리자 Bearer 토큰이 붙어 있다 — 외부 API는 사용자 토큰을 받지 않는다.
        mockMvc.perform(get("/api/external/tasks/{externalRef}", "WMS-ORDER-NONE"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void externalRef만으로_접수하고_같은_externalRef로_재요청하면_기존_작업을_반환한다() throws Exception {
        Long siteId = createSite("SITE-EXT1", "외부연동1");
        Long originId = createStation(siteId, "ST-E1", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-E2", "출고", StationType.DROPOFF);

        String firstResponse = mockMvc.perform(post("/api/external/tasks").header(API_KEY_HEADER, API_KEY)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateExternalTaskRequest(originId, destinationId, "WMS-ORDER-1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long firstTaskId = objectMapper.readTree(firstResponse).get("id").asLong();

        String secondResponse = mockMvc.perform(post("/api/external/tasks").header(API_KEY_HEADER, API_KEY)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateExternalTaskRequest(originId, destinationId, "WMS-ORDER-1"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long secondTaskId = objectMapper.readTree(secondResponse).get("id").asLong();

        assertThat(secondTaskId).isEqualTo(firstTaskId);
    }

    @Test
    void Idempotency_Key_헤더만으로도_접수하고_재요청하면_기존_작업을_반환한다() throws Exception {
        Long siteId = createSite("SITE-EXT2", "외부연동2");
        Long originId = createStation(siteId, "ST-E3", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-E4", "출고", StationType.DROPOFF);

        String firstResponse = mockMvc.perform(post("/api/external/tasks").header(API_KEY_HEADER, API_KEY)
                        .header("Idempotency-Key", "ext-idem-1")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateExternalTaskRequest(originId, destinationId, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long firstTaskId = objectMapper.readTree(firstResponse).get("id").asLong();

        String secondResponse = mockMvc.perform(post("/api/external/tasks").header(API_KEY_HEADER, API_KEY)
                        .header("Idempotency-Key", "ext-idem-1")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateExternalTaskRequest(originId, destinationId, null))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long secondTaskId = objectMapper.readTree(secondResponse).get("id").asLong();

        assertThat(secondTaskId).isEqualTo(firstTaskId);
    }

    @Test
    void 헤더도_externalRef도_없으면_400을_반환한다() throws Exception {
        Long siteId = createSite("SITE-EXT3", "외부연동3");
        Long originId = createStation(siteId, "ST-E5", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-E6", "출고", StationType.DROPOFF);

        mockMvc.perform(post("/api/external/tasks").header(API_KEY_HEADER, API_KEY)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateExternalTaskRequest(originId, destinationId, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void externalRef로_상태를_폴링해_완료까지_추적할_수_있다() throws Exception {
        Long siteId = createSite("SITE-EXT4", "외부연동4");
        Long originId = createStation(siteId, "ST-E7", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-E8", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-EXT1", "외부로봇1");

        mockMvc.perform(post("/api/external/tasks").header(API_KEY_HEADER, API_KEY)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateExternalTaskRequest(originId, destinationId, "WMS-ORDER-2"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/external/tasks/{externalRef}", "WMS-ORDER-2").header(API_KEY_HEADER, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"));

        long taskId = objectMapper.readTree(
                mockMvc.perform(get("/api/external/tasks/{externalRef}", "WMS-ORDER-2").header(API_KEY_HEADER, API_KEY))
                        .andReturn().getResponse().getContentAsString()
        ).get("id").asLong();

        mockMvc.perform(post("/api/admin/tasks/{taskId}/assign", taskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignRobotRequest(robotId))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/worker/tasks/{taskId}/dispatch", taskId)).andExpect(status().isOk());
        mockMvc.perform(post("/api/worker/tasks/{taskId}/confirm-unload", taskId)).andExpect(status().isOk());

        mockMvc.perform(get("/api/external/tasks/{externalRef}", "WMS-ORDER-2").header(API_KEY_HEADER, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
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

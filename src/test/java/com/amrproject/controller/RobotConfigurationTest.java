package com.amrproject.controller;

import com.amrproject.dto.request.robot.CreateRobotConfigurationRequest;
import com.amrproject.dto.request.robot.CreateRobotRequest;
import com.amrproject.dto.request.site.CreateSiteRequest;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 6단계 완료 기준(유효하지 않은 설정 차단, 적용 상태 추적) 중 로봇 설정 부분을 검증한다.
 */
@AutoConfigureMockMvc
class RobotConfigurationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Test
    void 설정을_생성하면_시뮬레이터가_즉시_적용해_desired와_applied가_일치한다() throws Exception {
        Long siteId = createSite("SITE-CFG1", "설정물류센터1");
        Long robotId = createRobot(siteId, "ROBOT-CFG1", "설정로봇1");

        mockMvc.perform(post("/api/admin/robots/{robotId}/configurations", robotId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRobotConfigurationRequest(
                                new BigDecimal("50.0"), new BigDecimal("1.5"), new BigDecimal("500.0"), 20, 90
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo").value(1));

        mockMvc.perform(get("/api/admin/robots/{robotId}", robotId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desiredConfigVersion").value(1))
                .andExpect(jsonPath("$.appliedConfigVersion").value(1));
    }

    @Test
    void 충전_목표가_저배터리_임계값보다_작거나_같으면_400을_반환한다() throws Exception {
        Long siteId = createSite("SITE-CFG2", "설정물류센터2");
        Long robotId = createRobot(siteId, "ROBOT-CFG2", "설정로봇2");

        mockMvc.perform(post("/api/admin/robots/{robotId}/configurations", robotId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRobotConfigurationRequest(
                                new BigDecimal("50.0"), new BigDecimal("1.5"), new BigDecimal("500.0"), 90, 20
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 음수_최대속도는_400을_반환한다() throws Exception {
        Long siteId = createSite("SITE-CFG3", "설정물류센터3");
        Long robotId = createRobot(siteId, "ROBOT-CFG3", "설정로봇3");

        mockMvc.perform(post("/api/admin/robots/{robotId}/configurations", robotId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRobotConfigurationRequest(
                                new BigDecimal("50.0"), new BigDecimal("-1.0"), new BigDecimal("500.0"), 20, 90
                        ))))
                .andExpect(status().isBadRequest());
    }

    private Long createSite(String code, String name) throws Exception {
        String response = mockMvc.perform(post("/api/admin/sites")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSiteRequest(code, name))))
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

package com.amrproject.controller;

import com.amrproject.support.AbstractIntegrationTest;
import com.amrproject.support.TestAdminTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 35단계: Prometheus 메트릭 노출과 actuator 접근 규칙. prod 프로필처럼 prometheus를 노출하되, 테스트(MOCK 서블릿)는
 * 관리 포트를 따로 띄울 수 없어 애플리케이션 포트 규칙(헬스는 공개, 나머지 actuator는 관리자만)을 확인한다.
 * 관리 포트로 들어온 요청을 토큰 없이 허용하는 규칙은 docker compose로 확인한다(docs/DEPLOYMENT.md).
 *
 * <p>기본 관리자 헤더가 자동으로 붙지 않도록 MockMvc를 직접 만든다(AuthenticationTest와 같은 이유).
 */
@TestPropertySource(properties = "management.endpoints.web.exposure.include=health,prometheus")
class OperationalMetricsTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private TestAdminTokenProvider testAdminTokenProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void 헬스는_누구나_보고_메트릭은_관리자만_보며_업무_게이지가_포함된다() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer " + testAdminTokenProvider.issueAdminToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("amr_outbox_events_pending")))
                .andExpect(content().string(containsString("amr_tasks{status=\"RECOVERY_REQUIRED\"}")))
                .andExpect(content().string(containsString("amr_alarms_open")))
                .andExpect(content().string(containsString("hikaricp_connections_active")));
    }
}

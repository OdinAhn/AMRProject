package com.amrproject.robot.mqtt;

import com.amrproject.domain.entity.robot.Robot;
import com.amrproject.domain.entity.site.StationType;
import com.amrproject.dto.request.robot.CreateRobotRequest;
import com.amrproject.dto.request.site.CreateSiteRequest;
import com.amrproject.dto.request.site.CreateStationRequest;
import com.amrproject.dto.request.task.CreateTaskRequest;
import com.amrproject.repository.robot.RobotRepository;
import com.amrproject.service.task.TaskService;
import com.amrproject.support.AbstractIntegrationTest;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 18단계: MQTT/VDA5050 어댑터가 실제 브로커(Testcontainers)를 통해 명령을 완료(성공)시키거나,
 * 응답이 없으면 타임아웃 처리하는지 검증한다. 기본적으로 전체 스위트는 이 기능을 켜지 않으므로
 * (app.robot-gateway.mqtt.enabled=false가 기본) 이 클래스에서만 별도 Mosquitto 컨테이너와
 * 프로퍼티를 추가한다.
 */
@AutoConfigureMockMvc
class MqttRobotGatewayTest extends AbstractIntegrationTest {

    private static final String MANUFACTURER = "AMRProject";

    private static final GenericContainer<?> mosquitto =
            new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:1.6.15")).withExposedPorts(1883);

    static {
        mosquitto.start();
    }

    @DynamicPropertySource
    static void mqttProperties(DynamicPropertyRegistry registry) {
        registry.add("app.robot-gateway.mqtt.enabled", () -> "true");
        registry.add("app.robot-gateway.mqtt.broker-url", MqttRobotGatewayTest::brokerUrl);
        registry.add("app.robot-gateway.mqtt.manufacturer", () -> MANUFACTURER);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private TaskService taskService;

    @Autowired
    private RobotRepository robotRepository;

    @Test
    void 가상_VDA5050_로봇이_응답하면_명령이_SUCCEEDED로_끝난다() throws Exception {
        Long siteId = createSite("SITE-MQTT1", "MQTT 물류센터1");
        Long originId = createStation(siteId, "ST-MQTT1", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-MQTT2", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-MQTT1", "MQTT로봇1");
        Long taskId = createTask(originId, destinationId, "idem-mqtt-1");

        try (VirtualVda5050Robot virtualRobot = new VirtualVda5050Robot(brokerUrl(), MANUFACTURER)) {
            Robot robot = robotRepository.findById(robotId).orElseThrow();
            taskService.assignRobot(taskId, robot);
            taskService.start(taskId);
        }

        mockMvc.perform(get("/api/worker/tasks/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.steps[0].status").value("SUCCEEDED"));
    }

    @Test
    void 응답이_없으면_타임아웃_후_RECOVERY_REQUIRED로_전이한다() throws Exception {
        Long siteId = createSite("SITE-MQTT2", "MQTT 물류센터2");
        Long originId = createStation(siteId, "ST-MQTT3", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-MQTT4", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-MQTT2", "MQTT로봇2");
        Long taskId = createTask(originId, destinationId, "idem-mqtt-2");

        // 가상 로봇을 띄우지 않는다 — 응답이 없어 타임아웃되는지 확인.
        Robot robot = robotRepository.findById(robotId).orElseThrow();
        taskService.assignRobot(taskId, robot);
        taskService.start(taskId);

        mockMvc.perform(get("/api/worker/tasks/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECOVERY_REQUIRED"));
    }

    private static String brokerUrl() {
        return "tcp://" + mosquitto.getHost() + ":" + mosquitto.getMappedPort(1883);
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

    /** 실제 로봇 대신 order를 구독해 즉시 완료(driving:false) state로 응답하는 테스트 전용 더미. */
    private static final class VirtualVda5050Robot implements AutoCloseable {

        private final MqttClient client;
        private final JsonMapper jsonMapper = JsonMapper.builder().build();
        private final String manufacturer;

        VirtualVda5050Robot(String brokerUrl, String manufacturer) throws Exception {
            this.manufacturer = manufacturer;
            this.client = new MqttClient(brokerUrl, MqttClient.generateClientId(), new MemoryPersistence());
            client.connect();
            client.subscribe("uagv/v2/" + manufacturer + "/+/order", (topic, message) -> {
                String serialNumber = topic.split("/")[3];
                Vda5050Order order = jsonMapper.readValue(message.getPayload(), Vda5050Order.class);
                Vda5050State state = new Vda5050State(order.orderId(), false, List.of(), new Vda5050State.BatteryState(88.0));
                client.publish(
                        "uagv/v2/" + this.manufacturer + "/" + serialNumber + "/state",
                        new MqttMessage(jsonMapper.writeValueAsString(state).getBytes(StandardCharsets.UTF_8))
                );
            });
        }

        @Override
        public void close() throws Exception {
            client.disconnect();
            client.close();
        }
    }
}

package com.amrproject.robot.mqtt;

import com.amrproject.domain.entity.robot.ConnectionState;
import com.amrproject.domain.entity.robot.RobotLifecycleStatus;
import com.amrproject.domain.entity.site.StationType;
import com.amrproject.dto.request.robot.CreateRobotRequest;
import com.amrproject.dto.request.robot.UpdateRobotLifecycleStatusRequest;
import com.amrproject.dto.request.site.CreateSiteRequest;
import com.amrproject.dto.request.site.CreateStationRequest;
import com.amrproject.dto.request.task.CreateTaskRequest;
import com.amrproject.repository.robot.RobotCurrentStateRepository;
import com.amrproject.repository.robot.RobotRepository;
import com.amrproject.service.robot.RobotConnectionService;
import com.amrproject.service.task.TaskService;
import com.amrproject.support.AbstractIntegrationTest;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 44단계: 운영용 MQTT 브로커 보안. 저장소의 실제 브로커 설정 스크립트(infra/mosquitto/amr-entrypoint.sh)로 mosquitto 2를 띄워
 * 익명 접속 거부, 서버 계정으로 게이트웨이 동작, 로봇 계정의 토픽 권한(자기 주문만 읽기·자기 상태만 쓰기)을 확인한다.
 * TLS 리스너는 인증서가 필요해 여기서는 끄고, 실제 compose·가상 로봇으로 확인한다(docs/DEPLOYMENT.md §4-1).
 */
@AutoConfigureMockMvc
class MqttBrokerSecurityTest extends AbstractIntegrationTest {

    private static final String MANUFACTURER = "AMRProject";

    private static final GenericContainer<?> broker = new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2.0.20"))
            .withCopyFileToContainer(MountableFile.forHostPath("infra/mosquitto/amr-entrypoint.sh"), "/amr/amr-entrypoint.sh")
            .withCopyToContainer(Transferable.of("ROBOT-SEC1:robot-one-pw\r\nROBOT-SEC2:robot-two-pw\n# 주석\n"), "/amr/robot-credentials")
            .withEnv("MQTT_SERVER_USERNAME", "amr-server")
            .withEnv("MQTT_SERVER_PASSWORD", "server-secret")
            .withEnv("MQTT_MANUFACTURER", MANUFACTURER)
            .withCommand("sh", "/amr/amr-entrypoint.sh")
            .withExposedPorts(1883)
            .waitingFor(Wait.forLogMessage(".*mosquitto version .* running.*", 1));

    static {
        broker.start();
    }

    @DynamicPropertySource
    static void mqttProperties(DynamicPropertyRegistry registry) {
        registry.add("app.robot-gateway.mqtt.enabled", () -> "true");
        registry.add("app.robot-gateway.mqtt.broker-url", MqttBrokerSecurityTest::brokerUrl);
        registry.add("app.robot-gateway.mqtt.manufacturer", () -> MANUFACTURER);
        registry.add("app.robot-gateway.mqtt.username", () -> "amr-server");
        registry.add("app.robot-gateway.mqtt.password", () -> "server-secret");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private TaskService taskService;

    @Autowired
    private RobotRepository robotRepository;

    @Autowired
    private RobotCurrentStateRepository robotCurrentStateRepository;

    @Autowired
    private RobotConnectionService robotConnectionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 익명이나_틀린_비밀번호로는_접속할_수_없다() {
        assertThatThrownBy(() -> connect(null, null)).isInstanceOf(MqttException.class);
        assertThatThrownBy(() -> connect("ROBOT-SEC1", "wrong-password")).isInstanceOf(MqttException.class);
    }

    /** 서버 계정으로 붙은 게이트웨이가 주문을 보내고, 로봇 계정은 자기 주문을 받아 결과를 보고해 작업 단계가 끝난다. 다른 로봇의 주문은 받지 못한다. */
    @Test
    void 로봇은_자기_주문만_받고_보고한_결과로_작업이_진행된다() throws Exception {
        Long siteId = createSite("SITE-SEC1", "보안센터1");
        Long originId = createStation(siteId, "ST-SEC1", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-SEC2", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-SEC1", "보안로봇1");
        Long taskId = createTask(originId, destinationId, "idem-sec-1");

        MqttClient robot = connect("ROBOT-SEC1", "robot-one-pw");
        MqttClient otherRobot = connect("ROBOT-SEC2", "robot-two-pw");
        List<String> ownOrders = new CopyOnWriteArrayList<>();
        List<String> snoopedOrders = new CopyOnWriteArrayList<>();
        try {
            robot.subscribe("uagv/v2/" + MANUFACTURER + "/ROBOT-SEC1/order", (topic, message) -> {
                Vda5050Order order = objectMapper.readValue(message.getPayload(), Vda5050Order.class);
                ownOrders.add(order.orderId());
                Vda5050State finished = new Vda5050State(order.orderId(), false, List.of(), new Vda5050State.BatteryState(77.0));
                robot.publish("uagv/v2/" + MANUFACTURER + "/ROBOT-SEC1/state",
                        new MqttMessage(objectMapper.writeValueAsString(finished).getBytes(StandardCharsets.UTF_8)));
            });
            // 다른 로봇이 ROBOT-SEC1의 주문 토픽을 구독해도(ACL) 메시지가 전달되지 않는다
            otherRobot.subscribe("uagv/v2/" + MANUFACTURER + "/ROBOT-SEC1/order", (topic, message) -> snoopedOrders.add(topic));

            taskService.assignRobot(taskId, robotRepository.findById(robotId).orElseThrow());
            taskService.start(taskId);

            awaitUntil(() -> "SUCCEEDED".equals(jdbcTemplate.queryForObject(
                    "select status from task_steps where task_id = ? and step_no = 1", String.class, taskId)));
            Thread.sleep(500);
        } finally {
            disconnect(robot);
            disconnect(otherRobot);
        }
        assertThat(ownOrders).hasSize(1);
        assertThat(snoopedOrders).isEmpty();
    }

    /** 로봇은 다른 로봇 이름으로 상태를 보낼 수 없다(ACL) — 끊긴 로봇을 가짜 하트비트로 되살리지 못하고, 자기 계정으로 보내야 복구된다. */
    @Test
    void 다른_로봇의_상태는_보낼_수_없다() throws Exception {
        Long siteId = createSite("SITE-SEC2", "보안센터2");
        Long victimId = createRobot(siteId, "ROBOT-SEC2", "보안로봇2");
        mockMvc.perform(patch("/api/admin/robots/{robotId}/lifecycle-status", victimId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRobotLifecycleStatusRequest(RobotLifecycleStatus.IN_SERVICE))))
                .andExpect(status().isOk());
        jdbcTemplate.update("update robot_current_states set received_at = date_sub(received_at, interval 10 minute) where robot_id = ?", victimId);
        robotConnectionService.markStaleRobotsOffline();
        assertThat(connectionOf(victimId)).isEqualTo(ConnectionState.OFFLINE);

        String heartbeat = objectMapper.writeValueAsString(new Vda5050State(null, false, List.of(), new Vda5050State.BatteryState(50.0)));
        MqttClient attacker = connect("ROBOT-SEC1", "robot-one-pw");
        try {
            attacker.publish("uagv/v2/" + MANUFACTURER + "/ROBOT-SEC2/state", new MqttMessage(heartbeat.getBytes(StandardCharsets.UTF_8)));
        } finally {
            disconnect(attacker);
        }
        Thread.sleep(2000);
        assertThat(connectionOf(victimId)).isEqualTo(ConnectionState.OFFLINE);

        MqttClient owner = connect("ROBOT-SEC2", "robot-two-pw");
        try {
            owner.publish("uagv/v2/" + MANUFACTURER + "/ROBOT-SEC2/state", new MqttMessage(heartbeat.getBytes(StandardCharsets.UTF_8)));
        } finally {
            disconnect(owner);
        }
        awaitUntil(() -> connectionOf(victimId) == ConnectionState.ONLINE);
    }

    private ConnectionState connectionOf(Long robotId) {
        return robotCurrentStateRepository.findById(robotId).orElseThrow().getConnectionState();
    }

    private static MqttClient connect(String username, String password) throws MqttException {
        MqttClient client = new MqttClient(brokerUrl(), MqttClient.generateClientId(), new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        if (username != null) {
            options.setUserName(username);
            options.setPassword(password.toCharArray());
        }
        try {
            client.connect(options);
        } catch (MqttException e) {
            client.close();
            throw e;
        }
        return client;
    }

    private static void disconnect(MqttClient client) throws MqttException {
        client.disconnect();
        client.close();
    }

    private static String brokerUrl() {
        return "tcp://" + broker.getHost() + ":" + broker.getMappedPort(1883);
    }

    private static void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(condition.getAsBoolean()).as("10초 안에 조건이 충족되지 않음").isTrue();
    }

    private Long createTask(Long originId, Long destinationId, String idempotencyKey) throws Exception {
        return idOf(mockMvc.perform(post("/api/worker/tasks")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTaskRequest(originId, destinationId))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private Long createSite(String code, String name) throws Exception {
        return idOf(mockMvc.perform(post("/api/admin/sites")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSiteRequest(code, name))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private Long createStation(Long siteId, String code, String name, StationType stationType) throws Exception {
        return idOf(mockMvc.perform(post("/api/admin/sites/{siteId}/stations", siteId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateStationRequest(code, name, stationType))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private Long createRobot(Long siteId, String code, String name) throws Exception {
        return idOf(mockMvc.perform(post("/api/admin/robots")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRobotRequest(code, name, null, null, siteId))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private Long idOf(String response) {
        return objectMapper.readTree(response).get("id").asLong();
    }
}

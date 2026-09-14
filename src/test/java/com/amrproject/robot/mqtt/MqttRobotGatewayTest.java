package com.amrproject.robot.mqtt;

import com.amrproject.domain.entity.robot.ConnectionState;
import com.amrproject.domain.entity.robot.Robot;
import com.amrproject.domain.entity.robot.RobotCurrentState;
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
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 18단계: MQTT/VDA5050 어댑터가 실제 브로커(Testcontainers)를 통해 명령을 완료(성공)시키거나,
 * 응답이 없으면 타임아웃 처리하는지 검증한다. 기본적으로 전체 스위트는 이 기능을 켜지 않으므로
 * (app.robot-gateway.mqtt.enabled=false가 기본) 이 클래스에서만 별도 Mosquitto 컨테이너와
 * 프로퍼티를 추가한다.
 *
 * <p>39단계: 명령이 비동기라 요청은 결과를 기다리지 않고 끝나고, 결과·타임아웃은 나중에 반영된다 — 결과는 폴링으로 확인한다.
 * 타임아웃은 2초, 감시 주기는 0.3초로 줄였다.
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
        registry.add("app.robot-gateway.mqtt.command-timeout-seconds", () -> "2");
        registry.add("app.robot-gateway.mqtt.command-timeout-check-ms", () -> "300");
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
            awaitUntil(() -> "SUCCEEDED".equals(stepStatus(taskId, 1)));
        }

        mockMvc.perform(get("/api/worker/tasks/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.steps[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.steps[1].stepType").value("CONFIRM_LOAD"))
                .andExpect(jsonPath("$.steps[1].status").value("RUNNING"));
        assertThat(commandStatus(taskId)).isEqualTo("SUCCEEDED");
        assertThat(robotCurrentStateRepository.findById(robotId).orElseThrow().getBatteryPct()).isEqualTo(88);
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
        Instant before = Instant.now();
        taskService.start(taskId);
        // 39단계: 요청(트랜잭션)은 결과를 기다리지 않고 바로 끝난다 — 예전엔 여기서 3초를 잠금을 쥔 채 기다렸다
        assertThat(Duration.between(before, Instant.now())).isLessThan(Duration.ofSeconds(1));
        mockMvc.perform(get("/api/worker/tasks/{taskId}", taskId))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.steps[0].status").value("RUNNING"));

        awaitUntil(() -> "RECOVERY_REQUIRED".equals(taskStatus(taskId)));
        assertThat(commandStatus(taskId)).isEqualTo("UNKNOWN");
    }

    /**
     * 39단계: 이동 중(명령 진행 중)에는 일시정지를 거절하고 버튼도 주지 않는다.
     * 40단계: 타임아웃으로 결과 불명이 된 이동을 로봇이 나중에 "도착"으로 보고하면 작업이 자동으로 이어지고 복구 필요 알람이 해소된다.
     */
    @Test
    void 이동_중에는_일시정지를_거절하고_타임아웃_뒤_도착_보고가_오면_작업을_자동으로_이어간다() throws Exception {
        Long siteId = createSite("SITE-MQTT4", "MQTT 물류센터4");
        Long originId = createStation(siteId, "ST-MQTT5", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-MQTT6", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-MQTT4", "MQTT로봇4");
        Long taskId = createTask(originId, destinationId, "idem-mqtt-4");
        Robot robot = robotRepository.findById(robotId).orElseThrow();
        taskService.assignRobot(taskId, robot);
        taskService.start(taskId);

        mockMvc.perform(get("/api/admin/tasks/{taskId}", taskId))
                .andExpect(jsonPath("$.allowedActions[?(@ == 'PAUSE')]").isEmpty());
        mockMvc.perform(post("/api/admin/tasks/{taskId}/pause", taskId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_COMMAND_IN_PROGRESS"));

        awaitUntil(() -> "RECOVERY_REQUIRED".equals(taskStatus(taskId)));
        String orderId = jdbcTemplate.queryForObject(
                "select c.command_no from robot_commands c join task_steps s on s.id = c.task_step_id where s.task_id = ?", String.class, taskId);
        publishState("ROBOT-MQTT4", new Vda5050State(orderId, false, List.of(), new Vda5050State.BatteryState(70.0)));

        awaitUntil(() -> "RUNNING".equals(taskStatus(taskId)));
        assertThat(commandStatus(taskId)).isEqualTo("SUCCEEDED");
        assertThat(stepStatus(taskId, 1)).isEqualTo("SUCCEEDED");
        assertThat(stepStatus(taskId, 2)).isEqualTo("RUNNING");
        assertThat(jdbcTemplate.queryForObject(
                "select status from alarms where alarm_type = 'TASK_RECOVERY_REQUIRED' and source_type = 'TASK' and source_id = ? order by id desc limit 1",
                String.class, taskId)).isEqualTo("RESOLVED");
        assertThat(jdbcTemplate.queryForList("select event_type from task_events where task_id = ? order by id", String.class, taskId))
                .endsWith("RECOVERY_REQUIRED", "RECOVERY_AUTO_RESUMED");
    }

    /** 40단계: 결과 불명 뒤 운영자가 그 로봇을 비상정지했다면, 늦은 도착 보고가 와도 작업을 자동으로 이어가지 않는다. */
    @Test
    void 결과_불명_뒤_비상정지한_로봇의_늦은_도착_보고로는_작업을_이어가지_않는다() throws Exception {
        Long siteId = createSite("SITE-MQTT5", "MQTT 물류센터5");
        Long originId = createStation(siteId, "ST-MQTT7", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-MQTT8", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-MQTT5", "MQTT로봇5");
        Long taskId = createTask(originId, destinationId, "idem-mqtt-5");
        taskService.assignRobot(taskId, robotRepository.findById(robotId).orElseThrow());
        taskService.start(taskId);
        awaitUntil(() -> "RECOVERY_REQUIRED".equals(taskStatus(taskId)));

        mockMvc.perform(post("/api/admin/robots/{robotId}/emergency-stop", robotId)).andExpect(status().isOk());
        publishState("ROBOT-MQTT5", new Vda5050State(moveCommandNo(taskId), false, List.of(), new Vda5050State.BatteryState(70.0)));

        awaitUntil(() -> "SUCCEEDED".equals(moveCommandStatus(taskId)));
        assertThat(taskStatus(taskId)).isEqualTo("RECOVERY_REQUIRED");
    }

    /**
     * 40단계: 이동 중(적재 전) 작업을 취소하면 로봇에 VDA 5050 cancelOrder를 커밋 뒤에 보내고, 진행 중이던 이동 명령은 CANCELED 사유로
     * FAILED 처리한다 — 늦은 state는 무시되고, 타임아웃 감시가 로봇을 통신 두절로 보고하지도 않는다.
     */
    @Test
    void 이동_중_취소하면_로봇에_주문_취소를_보내고_진행_중_명령을_끝낸다() throws Exception {
        Long siteId = createSite("SITE-MQTT6", "MQTT 물류센터6");
        Long originId = createStation(siteId, "ST-MQTT9", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-MQTT10", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-MQTT6", "MQTT로봇6");
        Long taskId = createTask(originId, destinationId, "idem-mqtt-6");

        List<String> instantActions = new CopyOnWriteArrayList<>();
        MqttClient listener = new MqttClient(brokerUrl(), MqttClient.generateClientId(), new MemoryPersistence());
        listener.connect();
        try {
            listener.subscribe("uagv/v2/" + MANUFACTURER + "/ROBOT-MQTT6/instantActions",
                    (topic, message) -> instantActions.add(new String(message.getPayload(), StandardCharsets.UTF_8)));
            taskService.assignRobot(taskId, robotRepository.findById(robotId).orElseThrow());
            taskService.start(taskId);
            awaitUntil(() -> "RUNNING".equals(moveCommandStatus(taskId)));

            mockMvc.perform(post("/api/admin/tasks/{taskId}/cancel", taskId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELED"));
            awaitUntil(() -> !instantActions.isEmpty());
        } finally {
            listener.disconnect();
            listener.close();
        }

        assertThat(instantActions.get(0)).contains("\"actionType\":\"cancelOrder\"");
        assertThat(moveCommandStatus(taskId)).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForList(
                "select e.event_type from command_events e join robot_commands c on c.id = e.command_id where c.command_no = ? order by e.id",
                String.class, moveCommandNo(taskId))).endsWith("CANCELED");
        awaitUntil(() -> "ACCEPTED".equals(jdbcTemplate.queryForObject(
                "select c.status from robot_commands c join task_steps s on s.id = c.task_step_id where s.task_id = ? and c.command_type = 'CANCEL'",
                String.class, taskId)));

        // 타임아웃(2초)이 지나도 끝난 명령은 감시 대상이 아니다 — 로봇이 통신 두절로 보고되지 않는다
        Thread.sleep(3000);
        assertThat(moveCommandStatus(taskId)).isEqualTo("FAILED");
        assertThat(robotCurrentStateRepository.findById(robotId).orElseThrow().getConnectionState()).isEqualTo(ConnectionState.ONLINE);
        assertThat(taskStatus(taskId)).isEqualTo("CANCELED");
    }

    /** 40단계: 이동 중 비상정지하면 진행 중이던 이동 명령을 EMERGENCY_STOPPED 사유로 끝내 타임아웃 감시가 로봇을 통신 두절로 보고하지 않는다. */
    @Test
    void 이동_중_비상정지하면_진행_중_명령을_끝내고_작업은_복구_필요가_된다() throws Exception {
        Long siteId = createSite("SITE-MQTT7", "MQTT 물류센터7");
        Long originId = createStation(siteId, "ST-MQTT11", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-MQTT12", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-MQTT7", "MQTT로봇7");
        Long taskId = createTask(originId, destinationId, "idem-mqtt-7");
        taskService.assignRobot(taskId, robotRepository.findById(robotId).orElseThrow());
        taskService.start(taskId);
        awaitUntil(() -> "RUNNING".equals(moveCommandStatus(taskId)));

        mockMvc.perform(post("/api/admin/robots/{robotId}/emergency-stop", robotId)).andExpect(status().isOk());

        assertThat(taskStatus(taskId)).isEqualTo("RECOVERY_REQUIRED");
        assertThat(moveCommandStatus(taskId)).isEqualTo("FAILED");
        Thread.sleep(3000);
        assertThat(moveCommandStatus(taskId)).isEqualTo("FAILED");
        assertThat(robotCurrentStateRepository.findById(robotId).orElseThrow().getConnectionState()).isEqualTo(ConnectionState.ONLINE);
    }

    private String moveCommandNo(Long taskId) {
        return jdbcTemplate.queryForObject(
                "select c.command_no from robot_commands c join task_steps s on s.id = c.task_step_id where s.task_id = ? and c.command_type = 'MOVE_TO' order by c.id desc limit 1",
                String.class, taskId);
    }

    private String moveCommandStatus(Long taskId) {
        return jdbcTemplate.queryForObject(
                "select c.status from robot_commands c join task_steps s on s.id = c.task_step_id where s.task_id = ? and c.command_type = 'MOVE_TO' order by c.id desc limit 1",
                String.class, taskId);
    }

    private void publishState(String robotCode, Vda5050State state) throws Exception {
        MqttClient robotClient = new MqttClient(brokerUrl(), MqttClient.generateClientId(), new MemoryPersistence());
        robotClient.connect();
        try {
            robotClient.publish("uagv/v2/" + MANUFACTURER + "/" + robotCode + "/state",
                    new MqttMessage(objectMapper.writeValueAsString(state).getBytes(StandardCharsets.UTF_8)));
        } finally {
            robotClient.disconnect();
            robotClient.close();
        }
    }

    private String taskStatus(Long taskId) {
        return jdbcTemplate.queryForObject("select status from tasks where id = ?", String.class, taskId);
    }

    private String stepStatus(Long taskId, int stepNo) {
        return jdbcTemplate.queryForObject("select status from task_steps where task_id = ? and step_no = ?", String.class, taskId, stepNo);
    }

    private String commandStatus(Long taskId) {
        return jdbcTemplate.queryForObject(
                "select c.status from robot_commands c join task_steps s on s.id = c.task_step_id where s.task_id = ? order by c.id desc limit 1",
                String.class, taskId);
    }

    /** 비동기 반영을 최대 10초 기다린다. */
    private static void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(condition.getAsBoolean()).as("10초 안에 조건이 충족되지 않음").isTrue();
    }

    /**
     * 29단계: 주문과 무관한 state 메시지(orderId 없음)도 하트비트로 기록돼, 하트비트가 끊겨 OFFLINE이 된 로봇이
     * 텔레메트리를 다시 보내면 ONLINE으로 복구되고 배터리가 반영된다(콜백 스레드가 아니라 별도 스레드에서 비동기 처리).
     */
    @Test
    void 주문과_무관한_state_메시지로도_끊긴_연결이_복구된다() throws Exception {
        Long siteId = createSite("SITE-MQTT3", "MQTT 물류센터3");
        Long robotId = createRobot(siteId, "ROBOT-MQTT3", "MQTT로봇3");
        mockMvc.perform(patch("/api/admin/robots/{robotId}/lifecycle-status", robotId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRobotLifecycleStatusRequest(RobotLifecycleStatus.IN_SERVICE))))
                .andExpect(status().isOk());
        jdbcTemplate.update("update robot_current_states set received_at = date_sub(received_at, interval 10 minute) where robot_id = ?", robotId);
        robotConnectionService.markStaleRobotsOffline();
        assertThat(robotCurrentStateRepository.findById(robotId).orElseThrow().getConnectionState()).isEqualTo(ConnectionState.OFFLINE);

        MqttClient robotClient = new MqttClient(brokerUrl(), MqttClient.generateClientId(), new MemoryPersistence());
        robotClient.connect();
        try {
            Vda5050State heartbeat = new Vda5050State(null, false, List.of(), new Vda5050State.BatteryState(42.0));
            robotClient.publish(
                    "uagv/v2/" + MANUFACTURER + "/ROBOT-MQTT3/state",
                    new MqttMessage(objectMapper.writeValueAsString(heartbeat).getBytes(StandardCharsets.UTF_8))
            );
        } finally {
            robotClient.disconnect();
            robotClient.close();
        }

        RobotCurrentState state = null;
        for (int i = 0; i < 50; i++) {
            state = robotCurrentStateRepository.findById(robotId).orElseThrow();
            if (state.getConnectionState() == ConnectionState.ONLINE) {
                break;
            }
            Thread.sleep(100);
        }
        assertThat(state.getConnectionState()).isEqualTo(ConnectionState.ONLINE);
        assertThat(state.getBatteryPct()).isEqualTo(42);
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

package com.amrproject.robot.mqtt;

import com.amrproject.domain.entity.alarm.AlarmSeverity;
import com.amrproject.domain.entity.alarm.AlarmSourceType;
import com.amrproject.domain.entity.alarm.AlarmType;
import com.amrproject.domain.entity.robot.CommandEvent;
import com.amrproject.domain.entity.robot.ConnectionState;
import com.amrproject.domain.entity.robot.MotionState;
import com.amrproject.domain.entity.robot.OperationMode;
import com.amrproject.domain.entity.robot.Robot;
import com.amrproject.domain.entity.robot.RobotCommand;
import com.amrproject.domain.entity.robot.RobotConfiguration;
import com.amrproject.domain.entity.robot.RobotCurrentState;
import com.amrproject.domain.entity.robot.RobotStateSnapshot;
import com.amrproject.domain.entity.task.Task;
import com.amrproject.domain.entity.task.TaskStep;
import com.amrproject.repository.robot.CommandEventRepository;
import com.amrproject.repository.robot.RobotCurrentStateRepository;
import com.amrproject.repository.robot.RobotStateSnapshotRepository;
import com.amrproject.robot.RobotGateway;
import com.amrproject.service.alarm.AlarmService;
import com.amrproject.service.robot.RobotConnectionService;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 18단계: VDA 5050(축소 구현)을 MQTT로 주고받는 {@link RobotGateway} 어댑터(옵트인,
 * 기본 비활성 — docs/API.md §7). 실제 로봇은 없어 로컬 브로커 + 테스트용 "가상 VDA5050
 * 로봇"으로 검증한다.
 *
 * <p><b>동기식-타임아웃 한계</b>: {@link RobotGateway#dispatch}는 계약상 동기식이라(호출이
 * 끝나면 명령이 최종 상태여야 함) 실제로는 비동기인 MQTT 통신을 {@link #TIMEOUT_SECONDS}
 * 동안 기다렸다가 결과를 반환하는 방식으로 맞춘다 — 실제 로봇은 응답이 이보다 훨씬 늦을 수
 * 있어 이 방식은 프로덕션에 적합하지 않다(사용자 확인 후 데모/검증 목적으로 축소한 설계).
 */
@Component
@Primary
@ConditionalOnProperty(name = "app.robot-gateway.mqtt.enabled", havingValue = "true")
public class MqttRobotGateway implements RobotGateway, MqttCallback {

    private static final String TOPIC_PREFIX = "uagv/v2/";
    private static final Logger log = LoggerFactory.getLogger(MqttRobotGateway.class);
    private static final long TIMEOUT_SECONDS = 3;

    private final MqttClient mqttClient;
    private final String manufacturer;
    private final JsonMapper jsonMapper;
    private final CommandEventRepository commandEventRepository;
    private final RobotCurrentStateRepository robotCurrentStateRepository;
    private final RobotStateSnapshotRepository robotStateSnapshotRepository;
    private final AlarmService alarmService;
    private final RobotConnectionService robotConnectionService;

    private final Map<String, CompletableFuture<Vda5050State>> pendingOrders = new ConcurrentHashMap<>();
    private final AtomicLong headerId = new AtomicLong();
    private final ExecutorService telemetryExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mqtt-telemetry");
        thread.setDaemon(true);
        return thread;
    });

    public MqttRobotGateway(
            MqttClient mqttClient,
            @Value("${app.robot-gateway.mqtt.manufacturer}") String manufacturer,
            JsonMapper jsonMapper,
            CommandEventRepository commandEventRepository,
            RobotCurrentStateRepository robotCurrentStateRepository,
            RobotStateSnapshotRepository robotStateSnapshotRepository,
            AlarmService alarmService,
            RobotConnectionService robotConnectionService
    ) throws MqttException {
        this.mqttClient = mqttClient;
        this.manufacturer = manufacturer;
        this.jsonMapper = jsonMapper;
        this.commandEventRepository = commandEventRepository;
        this.robotCurrentStateRepository = robotCurrentStateRepository;
        this.robotStateSnapshotRepository = robotStateSnapshotRepository;
        this.alarmService = alarmService;
        this.robotConnectionService = robotConnectionService;
        mqttClient.setCallback(this);
        mqttClient.subscribe(TOPIC_PREFIX + manufacturer + "/+/state");
    }

    @Override
    public void dispatch(RobotCommand command) {
        Robot robot = command.getRobot();
        RobotCurrentState state = currentStateOf(robot);
        String orderId = command.getCommandNo();
        CompletableFuture<Vda5050State> future = new CompletableFuture<>();
        pendingOrders.put(orderId, future);

        command.send();
        recordCommandEvent(command, "SENT");

        try {
            publishOrder(robot, orderId, resolveTargetNodeId(command));
        } catch (MqttException e) {
            pendingOrders.remove(orderId);
            command.markUnknown();
            recordCommandEvent(command, "TIMEOUT");
            report(robot, state, ConnectionState.OFFLINE, state.getMotionState(), state.getBatteryPct());
            return;
        }

        command.accept();
        recordCommandEvent(command, "ACCEPTED");
        command.startRunning();
        report(robot, state, ConnectionState.ONLINE, MotionState.MOVING, state.getBatteryPct());
        recordCommandEvent(command, "PROGRESS");

        Vda5050State result;
        try {
            result = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pendingOrders.remove(orderId);
            command.markUnknown();
            recordCommandEvent(command, "TIMEOUT");
            report(robot, state, ConnectionState.OFFLINE, MotionState.IDLE, state.getBatteryPct());
            return;
        } catch (InterruptedException | ExecutionException e) {
            // 27단계: 인터럽트 플래그는 실제로 인터럽트됐을 때만 복원한다 — 예전엔 ExecutionException에도
            // 설정해서, 이 스레드의 이후 블로킹 호출(DB 등)이 영문 모를 InterruptedException으로 실패할 수 있었다.
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            pendingOrders.remove(orderId);
            command.fail();
            recordCommandEvent(command, "FAILED");
            report(robot, state, ConnectionState.ONLINE, MotionState.IDLE, state.getBatteryPct());
            return;
        }

        int batteryPct = batteryPctFrom(result, state);
        if (result.errors() != null && !result.errors().isEmpty()) {
            command.fail();
            recordCommandEvent(command, "FAILED");
            report(robot, state, ConnectionState.ONLINE, MotionState.IDLE, batteryPct);
            return;
        }

        command.succeed();
        recordCommandEvent(command, "SUCCEEDED");
        report(robot, state, ConnectionState.ONLINE, MotionState.IDLE, batteryPct);
    }

    /**
     * 실제 로봇에 설정을 적용하는 프로토콜은 VDA5050 축소 구현 범위 밖이라
     * 시뮬레이터와 동일하게 즉시 성공으로 처리한다 (18단계 한계, docs/API.md §7).
     */
    @Override
    public void applyConfiguration(RobotConfiguration configuration) {
        configuration.getRobot().markConfigurationApplied(configuration);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        Vda5050State state = jsonMapper.readValue(message.getPayload(), Vda5050State.class);
        recordTelemetryAsync(topic, state);
        if (state.orderId() == null) {
            return;
        }
        boolean hasErrors = state.errors() != null && !state.errors().isEmpty();
        if (!Boolean.FALSE.equals(state.driving()) && !hasErrors) {
            return;
        }
        CompletableFuture<Vda5050State> future = pendingOrders.remove(state.orderId());
        if (future != null) {
            future.complete(state);
        }
    }

    /**
     * 29단계: 주문 완료 여부와 관계없이 모든 state 메시지를 하트비트로 기록한다(RobotConnectionService가 연결 감시에 쓴다).
     * Paho 콜백 스레드가 아니라 별도 스레드에서 처리한다 — {@link #dispatch}가 트랜잭션 안에서 이 로봇의 상태 행을
     * 잠근 채 완료 state를 기다리는 동안 콜백 스레드가 같은 행의 락을 기다리면, 완료 메시지를 처리하지 못해
     * 명령이 타임아웃(UNKNOWN)으로 끝난다.
     */
    private void recordTelemetryAsync(String topic, Vda5050State state) {
        String[] parts = topic.split("/");
        if (parts.length < 5) {
            return;
        }
        String serialNumber = parts[3];
        Integer batteryPct = state.batteryState() != null && state.batteryState().batteryCharge() != null
                ? state.batteryState().batteryCharge().intValue()
                : null;
        telemetryExecutor.execute(() -> {
            try {
                robotConnectionService.receiveTelemetry(serialNumber, batteryPct);
            } catch (Exception e) {
                log.warn("MQTT 텔레메트리 기록 실패: robotCode={}", serialNumber, e);
            }
        });
    }

    @PreDestroy
    void shutdownTelemetryExecutor() {
        telemetryExecutor.shutdown();
    }

    @Override
    public void connectionLost(Throwable cause) {
        // 자동 재연결(automaticReconnect)에 맡긴다 — 재연결 중 대기 중인 주문은 타임아웃으로 처리된다.
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 발행 확인은 필요 없다 — 완료 여부는 state 메시지로 판단한다.
    }

    private void publishOrder(Robot robot, String orderId, String nodeId) throws MqttException {
        Vda5050Order order = new Vda5050Order(
                headerId.incrementAndGet(),
                Instant.now().toString(),
                "2.0.0",
                manufacturer,
                robot.getCode(),
                orderId,
                0,
                List.of(new Vda5050Order.Node(nodeId, 0, true))
        );
        String json = jsonMapper.writeValueAsString(order);
        mqttClient.publish(TOPIC_PREFIX + manufacturer + "/" + robot.getCode() + "/order",
                new MqttMessage(json.getBytes(StandardCharsets.UTF_8)));
    }

    private String resolveTargetNodeId(RobotCommand command) {
        TaskStep step = command.getTaskStep();
        if (step == null) {
            return "UNKNOWN";
        }
        Task task = step.getTask();
        return switch (step.getStepType()) {
            case MOVE_TO_ORIGIN -> task.getOriginStation().getCode();
            case MOVE_TO_DESTINATION, MOVE_TO_CHARGING_STATION -> task.getDestinationStation().getCode();
            default -> "UNKNOWN";
        };
    }

    private int batteryPctFrom(Vda5050State state, RobotCurrentState current) {
        if (state.batteryState() != null && state.batteryState().batteryCharge() != null) {
            return state.batteryState().batteryCharge().intValue();
        }
        return current.getBatteryPct();
    }

    private RobotCurrentState currentStateOf(Robot robot) {
        return robotCurrentStateRepository.findById(robot.getId())
                .orElseGet(() -> robotCurrentStateRepository.save(new RobotCurrentState(robot)));
    }

    private void report(Robot robot, RobotCurrentState state, ConnectionState connection, MotionState motion, int batteryPct) {
        ConnectionState previousConnection = state.getConnectionState();
        state.report(
                connection, OperationMode.AUTO, motion, state.getSafetyState(),
                state.getLoadState(), state.getLocalizationState(), batteryPct, Instant.now()
        );
        robotStateSnapshotRepository.save(new RobotStateSnapshot(robot, state));
        reconcileConnectionAlarm(robot, previousConnection, connection);
    }

    /** VDA5050 축소 구현이라 연결 축만 알람과 연동한다 (위치추정/적재 축은 이번 범위 밖). */
    private void reconcileConnectionAlarm(Robot robot, ConnectionState previous, ConnectionState current) {
        if (current == ConnectionState.OFFLINE && previous != ConnectionState.OFFLINE) {
            alarmService.raise(AlarmType.ROBOT_COMM_LOST, AlarmSeverity.WARNING, AlarmSourceType.ROBOT, robot.getId(),
                    "로봇 통신이 두절되었습니다(MQTT): " + robot.getCode());
        } else if (current != ConnectionState.OFFLINE && previous == ConnectionState.OFFLINE) {
            alarmService.autoResolve(AlarmType.ROBOT_COMM_LOST, AlarmSourceType.ROBOT, robot.getId());
        }
    }

    private void recordCommandEvent(RobotCommand command, String eventType) {
        Instant now = Instant.now();
        commandEventRepository.save(new CommandEvent(command, eventType, null, now, now));
    }
}

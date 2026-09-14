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
import com.amrproject.domain.entity.robot.RobotCommandStatus;
import com.amrproject.domain.entity.robot.RobotCommandType;
import com.amrproject.domain.entity.robot.RobotConfiguration;
import com.amrproject.domain.entity.robot.RobotCurrentState;
import com.amrproject.domain.entity.robot.RobotStateSnapshot;
import com.amrproject.domain.entity.robot.SafetyState;
import com.amrproject.domain.entity.site.Station;
import com.amrproject.domain.entity.task.Task;
import com.amrproject.domain.entity.task.TaskStep;
import com.amrproject.repository.map.NodeRepository;
import com.amrproject.repository.robot.CommandEventRepository;
import com.amrproject.repository.robot.RobotCommandRepository;
import com.amrproject.repository.robot.RobotCurrentStateRepository;
import com.amrproject.repository.robot.RobotStateSnapshotRepository;
import com.amrproject.robot.RobotGateway;
import com.amrproject.service.alarm.AlarmService;
import com.amrproject.service.map.MapRouteService;
import com.amrproject.service.robot.RobotConnectionService;
import com.amrproject.service.robot.RobotStateEventRecorder;
import com.amrproject.service.robot.RobotStateReportService;
import com.amrproject.service.task.RobotCommandCompletionService;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 18단계: VDA 5050(축소 구현)을 MQTT로 주고받는 {@link RobotGateway} 어댑터(옵트인,
 * 기본 비활성 — docs/API.md §7). 실제 로봇은 없어 로컬 브로커 + 테스트용 "가상 VDA5050
 * 로봇"으로 검증한다.
 *
 * <p><b>39단계 — 비동기 명령</b>: 예전엔 {@link #dispatch}가 요청 트랜잭션 안에서(작업·로봇·작업장 행 잠금을 쥔 채) 완료 state를
 * 최대 3초 기다렸다. 이제는
 * <ol>
 *   <li>요청 트랜잭션에서는 명령을 SENT로 기록만 하고 반환한다(작업 단계는 RUNNING으로 남음).</li>
 *   <li>트랜잭션이 커밋된 뒤 order를 발행하고, 별도 트랜잭션으로 ACCEPTED→RUNNING을 기록한다. 커밋 전에 발행하면 롤백된 명령으로
 *       로봇이 움직일 수 있어서다.</li>
 *   <li>완료 state(driving=false 또는 errors)가 오면 콜백 스레드가 아닌 별도 스레드에서 {@link RobotCommandCompletionService}로
 *       명령·로봇 상태·작업을 한 트랜잭션에 반영한다.</li>
 *   <li>{@code command-timeout-seconds} 안에 결과가 없으면 감시 스케줄러가 UNKNOWN으로 두고 작업을 복구 필요로 보낸다.</li>
 * </ol>
 * 시뮬레이터는 메모리에서 즉시 끝나 기다리는 동안 잠금을 쥐지 않으므로 기존처럼 동기식이다({@link RobotGateway} 계약 참고).
 */
@Component
@Primary
@ConditionalOnProperty(name = "app.robot-gateway.mqtt.enabled", havingValue = "true")
public class MqttRobotGateway implements RobotGateway, MqttCallback {

    private static final String TOPIC_PREFIX = "uagv/v2/";
    private static final Logger log = LoggerFactory.getLogger(MqttRobotGateway.class);

    private final MqttClient mqttClient;
    private final String manufacturer;
    private final JsonMapper jsonMapper;
    private final CommandEventRepository commandEventRepository;
    private final RobotCurrentStateRepository robotCurrentStateRepository;
    private final RobotStateSnapshotRepository robotStateSnapshotRepository;
    private final AlarmService alarmService;
    private final RobotConnectionService robotConnectionService;
    private final RobotStateEventRecorder robotStateEventRecorder;
    private final RobotCommandRepository robotCommandRepository;
    private final RobotStateReportService robotStateReportService;
    private final MapRouteService mapRouteService;
    private final NodeRepository nodeRepository;
    /** 완료 서비스 → TaskService → RobotGateway(이 빈)로 이어지는 생성자 순환을 끊기 위해 쓸 때 꺼낸다 */
    private final ObjectProvider<RobotCommandCompletionService> completionService;
    private final TransactionTemplate transactionTemplate;
    private final long commandTimeoutSeconds;

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
            RobotConnectionService robotConnectionService,
            RobotStateEventRecorder robotStateEventRecorder,
            RobotCommandRepository robotCommandRepository,
            RobotStateReportService robotStateReportService,
            MapRouteService mapRouteService,
            NodeRepository nodeRepository,
            ObjectProvider<RobotCommandCompletionService> completionService,
            PlatformTransactionManager transactionManager,
            @Value("${app.robot-gateway.mqtt.command-timeout-seconds:300}") long commandTimeoutSeconds
    ) throws MqttException {
        this.mqttClient = mqttClient;
        this.manufacturer = manufacturer;
        this.jsonMapper = jsonMapper;
        this.commandEventRepository = commandEventRepository;
        this.robotCurrentStateRepository = robotCurrentStateRepository;
        this.robotStateSnapshotRepository = robotStateSnapshotRepository;
        this.alarmService = alarmService;
        this.robotConnectionService = robotConnectionService;
        this.robotStateEventRecorder = robotStateEventRecorder;
        this.robotCommandRepository = robotCommandRepository;
        this.robotStateReportService = robotStateReportService;
        this.mapRouteService = mapRouteService;
        this.nodeRepository = nodeRepository;
        this.completionService = completionService;
        // afterCommit 안에서는 커밋이 끝난 원래 트랜잭션 자원이 아직 묶여 있어, 기본 전파(REQUIRED)면 그 트랜잭션에 참여만 하고
        // 커밋되지 않는다 — 항상 새 트랜잭션으로 기록한다.
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.commandTimeoutSeconds = commandTimeoutSeconds;
        mqttClient.setCallback(this);
        mqttClient.subscribe(TOPIC_PREFIX + manufacturer + "/+/state");
    }

    @Override
    public void dispatch(RobotCommand command) {
        Robot robot = command.getRobot();

        if (command.getCommandType() == RobotCommandType.EMERGENCY_STOP) {
            // 비상정지는 커밋을 기다리지 않고 바로 보낸다 — 트랜잭션이 롤백돼도 로봇이 멈춘 쪽이 안전하다
            emergencyStop(robot, command);
            return;
        }
        if (command.getCommandType() == RobotCommandType.CANCEL) {
            cancelOrder(robot, command);
            return;
        }

        command.send();
        recordCommandEvent(command, "SENT");
        // 상태를 보고한 적 없는 로봇이면 상태 행을 요청 트랜잭션에서 미리 만든다 — 발행 확인과 결과 반영이 서로 다른 트랜잭션에서
        // 동시에 처음 만들려다 기본 키가 충돌하면 결과 반영이 롤백된다(검증 중 발견).
        currentStateOf(robot);

        // 트랜잭션 밖(지연 로딩 불가)에서 발행하므로 필요한 값은 지금 꺼내 둔다
        Long commandId = command.getId();
        String orderId = command.getCommandNo();
        String robotCode = robot.getCode();
        String targetNodeId = resolveTargetNodeId(command);
        List<Vda5050Order.Action> actions = command.getCommandType() == RobotCommandType.START_CHARGE
                ? List.of(new Vda5050Order.Action("startCharging", orderId, "HARD"))
                : List.of();
        Runnable publish = () -> publishAfterCommit(commandId, orderId, robotCode, targetNodeId, actions);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }

    /** 커밋된 명령을 발행하고, 발행되면 ACCEPTED→RUNNING, 실패하면 UNKNOWN(작업 복구 필요)으로 별도 트랜잭션에서 기록한다. */
    private void publishAfterCommit(Long commandId, String orderId, String robotCode, String targetNodeId, List<Vda5050Order.Action> actions) {
        try {
            publishOrder(robotCode, orderId, targetNodeId, actions);
        } catch (MqttException e) {
            log.warn("MQTT order 발행 실패: commandNo={} robot={}", orderId, robotCode, e);
            transactionTemplate.executeWithoutResult(status -> completionService.getObject().markUnknown(commandId, "TIMEOUT",
                    command -> reportCurrent(command.getRobot(), ConnectionState.OFFLINE, null, null)));
            return;
        }
        transactionTemplate.executeWithoutResult(status -> robotCommandRepository.findByIdForUpdate(commandId)
                // 결과가 이 기록보다 먼저 도착해 이미 끝났으면 건드리지 않는다
                .filter(command -> command.getStatus() == RobotCommandStatus.SENT)
                .ifPresent(command -> {
                    command.accept();
                    recordCommandEvent(command, "ACCEPTED");
                    command.startRunning();
                    recordCommandEvent(command, "PROGRESS");
                    reportCurrent(command.getRobot(), ConnectionState.ONLINE, MotionState.MOVING, null);
                }));
    }

    /** 39단계: 결과가 오지 않은 명령을 주기적으로 찾아 UNKNOWN으로 두고 작업을 복구 필요로 보낸다. */
    @Scheduled(fixedDelayString = "${app.robot-gateway.mqtt.command-timeout-check-ms:5000}")
    void timeOutStaleCommands() {
        for (Long commandId : completionService.getObject().findTimedOut(commandTimeoutSeconds)) {
            try {
                completionService.getObject().markUnknown(commandId, "TIMEOUT",
                        command -> reportCurrent(command.getRobot(), ConnectionState.OFFLINE, MotionState.IDLE, null));
            } catch (RuntimeException e) {
                log.warn("MQTT 명령 타임아웃 처리 실패: commandId={}", commandId, e);
            }
        }
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
        Vda5050State state;
        try {
            state = jsonMapper.readValue(message.getPayload(), Vda5050State.class);
        } catch (RuntimeException e) {
            // 43단계: 예외가 콜백 밖으로 나가면 Paho가 브로커 연결을 끊는다 — 잘못된 메시지 하나로 모든 로봇 통신이 멈추지 않게 버린다
            log.warn("MQTT state 메시지를 해석할 수 없어 무시합니다: topic={}", topic, e);
            return;
        }
        recordTelemetryAsync(topic, state);
        // VDA 5050 로봇은 주문이 없을 때 orderId를 빈 문자열로 보낸다
        if (state.orderId() == null || state.orderId().isBlank()) {
            return;
        }
        if (!state.orderFinished()) {
            return;
        }
        boolean hasErrors = state.hasErrors();
        // 39단계: DB 작업은 Paho 콜백 스레드에서 하지 않는다(콜백이 막히면 뒤따르는 메시지를 못 받는다)
        Integer batteryPct = state.batteryState() != null && state.batteryState().batteryCharge() != null
                ? state.batteryState().batteryCharge().intValue()
                : null;
        telemetryExecutor.execute(() -> {
            try {
                completionService.getObject().complete(state.orderId(), !hasErrors,
                        command -> reportCurrent(command.getRobot(), ConnectionState.ONLINE, MotionState.IDLE, batteryPct));
            } catch (Exception e) {
                log.warn("MQTT 명령 결과 반영 실패: orderId={}", state.orderId(), e);
            }
        });
    }

    /**
     * 29단계: 주문 완료 여부와 관계없이 모든 state 메시지를 하트비트로 기록한다(RobotConnectionService가 연결 감시에 쓴다).
     * Paho 콜백 스레드가 아니라 별도 스레드에서 처리한다 — 콜백 스레드가 DB 잠금을 기다리면 뒤따르는 메시지(명령 완료 등)를
     * 처리하지 못한다. 39단계부터 명령 결과 반영도 같은 스레드에서 순서대로 처리한다.
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
        RobotStateReportService.Report report = toReport(serialNumber, state);
        telemetryExecutor.execute(() -> {
            try {
                robotConnectionService.receiveTelemetry(serialNumber, batteryPct);
                // 41단계: 동작 모드·안전 상태·위치·즉시 동작 결과
                robotStateReportService.apply(report);
            } catch (Exception e) {
                log.warn("MQTT 텔레메트리 기록 실패: robotCode={}", serialNumber, e);
            }
        });
    }

    /** 41단계: VDA 5050 state 필드를 프로토콜과 무관한 도메인 보고로 바꾼다. 없거나 모르는 값은 null(변경 없음)로 둔다. */
    static RobotStateReportService.Report toReport(String robotCode, Vda5050State state) {
        OperationMode mode = state.operatingMode() == null ? null : switch (state.operatingMode()) {
            case "AUTOMATIC" -> OperationMode.AUTO;
            case "SEMIAUTOMATIC", "MANUAL", "TEACHIN" -> OperationMode.MANUAL;
            case "SERVICE" -> OperationMode.MAINTENANCE;
            default -> null;
        };
        SafetyState safety = null;
        if (state.safetyState() != null) {
            String eStop = state.safetyState().eStop();
            if (eStop != null && !"NONE".equals(eStop)) {
                safety = SafetyState.EMERGENCY_STOP;
            } else if (Boolean.TRUE.equals(state.safetyState().fieldViolation()) || Boolean.TRUE.equals(state.paused())) {
                // 43단계: startPause로 멈춘 로봇(paused)도 움직일 수 없으므로 보호 정지로 보고 배정에서 뺀다
                safety = SafetyState.PROTECTIVE_STOP;
            } else if (eStop != null || state.safetyState().fieldViolation() != null) {
                safety = SafetyState.NORMAL;
            }
        }
        List<RobotStateReportService.ActionResult> actionResults = state.actionStates() == null ? List.of()
                : state.actionStates().stream()
                        .filter(action -> action.actionId() != null)
                        .filter(action -> "FINISHED".equals(action.actionStatus()) || "FAILED".equals(action.actionStatus()))
                        .map(action -> new RobotStateReportService.ActionResult(action.actionId(), "FINISHED".equals(action.actionStatus())))
                        .toList();
        String lastNodeId = state.lastNodeId() == null || state.lastNodeId().isBlank() ? null : state.lastNodeId();
        return new RobotStateReportService.Report(robotCode, mode, safety, lastNodeId, actionResults);
    }

    @PreDestroy
    void shutdownTelemetryExecutor() {
        telemetryExecutor.shutdown();
    }

    @Override
    public void connectionLost(Throwable cause) {
        // 자동 재연결(automaticReconnect)에 맡긴다 — 결과를 받지 못한 명령은 타임아웃 감시가 처리한다(39단계).
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 발행 확인은 필요 없다 — 완료 여부는 state 메시지로 판단한다.
    }

    /**
     * 40단계: 이동 중 작업이 취소되면 VDA 5050 instantActions의 {@code cancelOrder}를 보낸다. 취소가 롤백되면 로봇을 세우면 안 되므로
     * 이동 명령처럼 커밋 뒤에 보내고, 보내지면 ACCEPTED(전달됨)까지만 둔다 — 로봇이 실제로 취소했는지는 이 축소 구현이 actionStates를
     * 파싱하지 않아 확인하지 않는다. 취소된 주문의 이동 명령은 호출자가 이미 FAILED로 끝냈으므로 뒤이은 state는 무시된다.
     */
    private void cancelOrder(Robot robot, RobotCommand command) {
        command.send();
        recordCommandEvent(command, "SENT");
        Long commandId = command.getId();
        String robotCode = robot.getCode();
        String actionId = command.getCommandNo();
        Runnable publish = () -> {
            try {
                publishInstantAction(robotCode, "cancelOrder", actionId, "NONE");
            } catch (MqttException e) {
                log.warn("MQTT cancelOrder 발행 실패: robot={}", robotCode, e);
                return;
            }
            transactionTemplate.executeWithoutResult(status -> robotCommandRepository.findByIdForUpdate(commandId)
                    .filter(sent -> sent.getStatus() == RobotCommandStatus.SENT)
                    .ifPresent(sent -> {
                        sent.accept();
                        recordCommandEvent(sent, "ACCEPTED");
                    }));
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }

    /**
     * 33단계: VDA 5050 instantActions의 {@code startPause}(HARD)로 즉시 멈추게 한다. 43단계에서 바로잡음 — 예전엔 {@code stopPause}를 보냈는데
     * 스펙에서 stopPause는 <b>일시정지 해제(재개)</b>라 실제 로봇은 반대로 동작했을 것이다. 표준에 원격 비상정지 동작은 없어 가장 가까운 startPause를 쓴다. 로봇이 실제로 멈췄는지는 이 축소 구현이 state의 안전 상태를
     * 파싱하지 않아 확인할 수 없으므로 명령은 ACCEPTED(전달됨)까지만 둔다 — SUCCEEDED로 표시하지 않는다(DESIGN.md §3).
     */
    private void emergencyStop(Robot robot, RobotCommand command) {
        command.send();
        recordCommandEvent(command, "SENT");
        try {
            publishInstantAction(robot.getCode(), "startPause", command.getCommandNo(), "HARD");
            command.accept();
            recordCommandEvent(command, "ACCEPTED");
        } catch (MqttException e) {
            command.markUnknown();
            recordCommandEvent(command, "TIMEOUT");
        }
    }

    private void publishInstantAction(String robotCode, String actionType, String actionId, String blockingType) throws MqttException {
        Map<String, Object> instantActions = Map.of(
                "headerId", headerId.incrementAndGet(),
                "timestamp", Instant.now().toString(),
                "version", "2.0.0",
                "manufacturer", manufacturer,
                "serialNumber", robotCode,
                "actions", List.of(Map.of("actionType", actionType, "actionId", actionId, "blockingType", blockingType))
        );
        mqttClient.publish(TOPIC_PREFIX + manufacturer + "/" + robotCode + "/instantActions",
                new MqttMessage(jsonMapper.writeValueAsString(instantActions).getBytes(StandardCharsets.UTF_8)));
    }

    private void publishOrder(String robotCode, String orderId, String nodeId, List<Vda5050Order.Action> actions) throws MqttException {
        Vda5050Order order = new Vda5050Order(
                headerId.incrementAndGet(),
                Instant.now().toString(),
                "2.0.0",
                manufacturer,
                robotCode,
                orderId,
                0,
                List.of(new Vda5050Order.Node(nodeId, 0, true, actions))
        );
        String json = jsonMapper.writeValueAsString(order);
        mqttClient.publish(TOPIC_PREFIX + manufacturer + "/" + robotCode + "/order",
                new MqttMessage(json.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 주문의 목적지 nodeId. 43단계: 실제 로봇은 이 서버의 작업장 코드가 아니라 지도 노드를 알므로, 작업장에 연결된 활성 지도 노드의
     * 코드를 보낸다. 지도나 연결이 없으면 예전처럼 작업장 코드를 보낸다. 충전(DOCK_AND_CHARGE)은 충전 작업장 노드다.
     */
    private String resolveTargetNodeId(RobotCommand command) {
        TaskStep step = command.getTaskStep();
        if (step == null) {
            return "UNKNOWN";
        }
        Task task = step.getTask();
        Station station = switch (step.getStepType()) {
            case MOVE_TO_ORIGIN -> task.getOriginStation();
            case MOVE_TO_DESTINATION, MOVE_TO_CHARGING_STATION, DOCK_AND_CHARGE -> task.getDestinationStation();
            default -> null;
        };
        if (station == null) {
            return "UNKNOWN";
        }
        return mapRouteService.findActiveVersionId(task.getSite().getId())
                .flatMap(versionId -> mapRouteService.findStationNodeId(station.getId(), versionId))
                .flatMap(nodeRepository::findById)
                .map(node -> node.getCode())
                .orElse(station.getCode());
    }

    /** 호출한 트랜잭션에서 로봇 현재 상태를 읽어 연결·동작·배터리를 반영한다. motion/batteryPct가 null이면 기존 값을 유지한다. */
    private void reportCurrent(Robot robot, ConnectionState connection, MotionState motion, Integer batteryPct) {
        RobotCurrentState state = currentStateOf(robot);
        report(robot, state, connection, motion != null ? motion : state.getMotionState(),
                batteryPct != null ? batteryPct : state.getBatteryPct());
    }

    private RobotCurrentState currentStateOf(Robot robot) {
        return robotCurrentStateRepository.findById(robot.getId())
                .orElseGet(() -> robotCurrentStateRepository.save(new RobotCurrentState(robot)));
    }

    private void report(Robot robot, RobotCurrentState state, ConnectionState connection, MotionState motion, int batteryPct) {
        ConnectionState previousConnection = state.getConnectionState();
        MotionState previousMotion = state.getMotionState();
        Integer previousBattery = state.getBatteryPct();
        state.report(
                connection, OperationMode.AUTO, motion, state.getSafetyState(),
                state.getLoadState(), state.getLocalizationState(), batteryPct, Instant.now()
        );
        robotStateSnapshotRepository.save(new RobotStateSnapshot(robot, state));
        if (previousConnection != connection || previousMotion != motion || !previousBattery.equals(batteryPct)) {
            robotStateEventRecorder.recordChanged(robot.getId()); // 30단계
        }
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

package com.amrproject.service.robot;

import com.amrproject.robot.SimulatorRobotGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 29단계: {@link RobotConnectionService}의 주기 실행. {@code app.robot.connection-monitor.enabled}가
 * false(기본값)면 아무것도 하지 않는다 — 테스트 스위트는 끄고 local 프로필에서만 켠다.
 *
 * <p>시뮬레이터 하트비트는 시뮬레이터가 실제 게이트웨이일 때(MQTT 비활성)만 친다 — MQTT 모드에서
 * 서버가 대신 하트비트를 치면 실제 로봇이 꺼져도 영원히 ONLINE으로 보인다. 통신 두절 장애가 주입된 로봇은
 * 시뮬레이터가 알려주는 목록으로 제외한다. 통신 두절 장애가 주입된 로봇은
 * 시뮬레이터가 알려주는 목록으로 제외한다.
 */
@Component
public class RobotConnectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RobotConnectionScheduler.class);

    private final RobotConnectionService robotConnectionService;
    private final SimulatorRobotGateway simulatorRobotGateway;
    private final boolean enabled;
    private final boolean mqttEnabled;

    public RobotConnectionScheduler(
            RobotConnectionService robotConnectionService,
            SimulatorRobotGateway simulatorRobotGateway,
            @Value("${app.robot.connection-monitor.enabled:false}") boolean enabled,
            @Value("${app.robot-gateway.mqtt.enabled:false}") boolean mqttEnabled
    ) {
        this.robotConnectionService = robotConnectionService;
        this.simulatorRobotGateway = simulatorRobotGateway;
        this.enabled = enabled;
        this.mqttEnabled = mqttEnabled;
    }

    @Scheduled(fixedDelayString = "${app.robot.simulator-heartbeat-interval-ms:5000}")
    public void simulatorHeartbeat() {
        if (enabled && !mqttEnabled) {
            robotConnectionService.heartbeatSimulatedRobots(simulatorRobotGateway.communicationLostRobotIds());
        }
    }

    @Scheduled(fixedDelayString = "${app.robot.connection-monitor.interval-ms:5000}")
    public void checkConnections() {
        if (!enabled) {
            return;
        }
        List<Long> offline = robotConnectionService.markStaleRobotsOffline();
        if (!offline.isEmpty()) {
            log.warn("하트비트가 끊겨 OFFLINE으로 전환한 로봇: {}", offline);
        }
    }
}

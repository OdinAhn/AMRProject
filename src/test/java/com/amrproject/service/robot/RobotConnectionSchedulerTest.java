package com.amrproject.service.robot;

import com.amrproject.robot.SimulatorRobotGateway;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 32단계: 서버 기동 직후 하트비트 타임아웃 동안은 연결 판정을 미루는지 DB 없이(Docker 불필요) 검증한다.
 */
class RobotConnectionSchedulerTest {

    private final RobotConnectionService robotConnectionService = Mockito.mock(RobotConnectionService.class);
    private final SimulatorRobotGateway simulatorRobotGateway = Mockito.mock(SimulatorRobotGateway.class);

    @Test
    void 기동_후_하트비트_타임아웃이_지나기_전에는_연결_판정을_하지_않는다() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-13T00:00:00Z"));
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        when(robotConnectionService.markStaleRobotsOffline()).thenReturn(List.of());
        RobotConnectionScheduler scheduler = new RobotConnectionScheduler(
                robotConnectionService, simulatorRobotGateway, true, false, 30, clock
        );

        now.set(now.get().plus(Duration.ofSeconds(29)));
        scheduler.checkConnections();
        verify(robotConnectionService, never()).markStaleRobotsOffline();

        now.set(now.get().plus(Duration.ofSeconds(1)));
        scheduler.checkConnections();
        verify(robotConnectionService, times(1)).markStaleRobotsOffline();
    }

    @Test
    void 시뮬레이터_하트비트는_기동_직후에도_바로_친다() {
        RobotConnectionScheduler scheduler = new RobotConnectionScheduler(
                robotConnectionService, simulatorRobotGateway, true, false, 30, Clock.systemUTC()
        );

        scheduler.simulatorHeartbeat();

        verify(robotConnectionService, times(1)).heartbeatSimulatedRobots(Mockito.any());
    }
}

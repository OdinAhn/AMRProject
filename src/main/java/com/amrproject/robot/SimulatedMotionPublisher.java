package com.amrproject.robot;

import com.amrproject.config.RedisConfig;
import com.amrproject.dto.response.event.EventEnvelope;
import com.amrproject.service.map.MapRouteService.Waypoint;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 30단계: 가상 로봇이 통로를 따라 움직이는 모습을 화면에 보여주기 위한 <b>표시 전용</b> 위치 이벤트 발행기.
 *
 * <p>시뮬레이터 명령은 4단계부터 즉시(동기) 완료되고, 그 계약은 {@code TaskService}와 테스트 전체가 의존한다 — 이 클래스는
 * 그 계약을 바꾸지 않는다. 명령이 커밋된 <b>뒤</b> 경로를 따라 일정 간격으로 보간한 좌표를 {@code robot.position}
 * 이벤트로 Redis 채널에 직접 흘려보낼 뿐이다. 트랜잭셔널 아웃박스(1초 폴링, DB 기록, Kafka 로그)는 초당 여러 번의 일시적
 * 좌표에 맞지 않아 우회한다 — 그래서 이 이벤트에는 {@code eventId}가 없고, 재접속 백로그로 재생되지도 않는다.
 * 로봇의 논리적 위치(지금 어느 노드에 있는가)는 {@code robot_current_states.current_node_id}가 기준이다.
 */
@Component
public class SimulatedMotionPublisher {

    public static final String EVENT_TYPE = "robot.position";
    private static final Logger log = LoggerFactory.getLogger(SimulatedMotionPublisher.class);
    private static final long FRAME_INTERVAL_MS = 200;

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final double unitsPerSecond;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "simulated-motion");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<Long, ScheduledFuture<?>> running = new ConcurrentHashMap<>();

    public SimulatedMotionPublisher(
            StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            @Value("${app.simulation.speed-units-per-second:20}") double unitsPerSecond
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.unitsPerSecond = unitsPerSecond;
    }

    /** 현재 트랜잭션이 커밋된 뒤(트랜잭션이 없으면 즉시) 경로 애니메이션을 시작한다. 같은 로봇의 이전 애니메이션은 멈춘다. */
    public void animateAfterCommit(Long robotId, Long mapVersionId, List<Waypoint> path) {
        if (path.size() < 2) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    start(robotId, mapVersionId, path);
                }
            });
        } else {
            start(robotId, mapVersionId, path);
        }
    }

    private void start(Long robotId, Long mapVersionId, List<Waypoint> path) {
        List<double[]> frames = interpolate(path);
        AtomicInteger index = new AtomicInteger();
        ScheduledFuture<?>[] self = new ScheduledFuture<?>[1];
        Runnable frame = () -> {
            int i = index.getAndIncrement();
            if (i >= frames.size()) {
                running.remove(robotId, self[0]);
                self[0].cancel(false);
                return;
            }
            publish(robotId, mapVersionId, frames.get(i), i == frames.size() - 1);
        };
        ScheduledFuture<?> previous = running.get(robotId);
        if (previous != null) {
            previous.cancel(false);
        }
        self[0] = scheduler.scheduleAtFixedRate(frame, 0, FRAME_INTERVAL_MS, TimeUnit.MILLISECONDS);
        running.put(robotId, self[0]);
    }

    /** 경로를 프레임 간격마다 이동할 거리로 나눈 좌표 목록. 마지막 프레임은 항상 도착 노드다. */
    private List<double[]> interpolate(List<Waypoint> path) {
        double step = unitsPerSecond * FRAME_INTERVAL_MS / 1000.0;
        List<double[]> frames = new ArrayList<>();
        frames.add(new double[]{path.getFirst().x(), path.getFirst().y()});
        for (int i = 1; i < path.size(); i++) {
            Waypoint from = path.get(i - 1);
            Waypoint to = path.get(i);
            double length = Math.hypot(to.x() - from.x(), to.y() - from.y());
            int segments = Math.max(1, (int) Math.ceil(length / step));
            for (int s = 1; s <= segments; s++) {
                double t = (double) s / segments;
                frames.add(new double[]{from.x() + (to.x() - from.x()) * t, from.y() + (to.y() - from.y()) * t});
            }
        }
        return frames;
    }

    private void publish(Long robotId, Long mapVersionId, double[] point, boolean arrived) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("mapVersionId", mapVersionId);
            payload.put("x", Math.round(point[0] * 100) / 100.0);
            payload.put("y", Math.round(point[1] * 100) / 100.0);
            payload.put("arrived", arrived);
            EventEnvelope envelope = new EventEnvelope(
                    null, EVENT_TYPE, "ROBOT", robotId, null, jsonMapper.writeValueAsString(payload), Instant.now()
            );
            redisTemplate.convertAndSend(RedisConfig.EVENT_CHANNEL, jsonMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            log.debug("robot.position 발행 실패: robotId={}", robotId, e);
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}

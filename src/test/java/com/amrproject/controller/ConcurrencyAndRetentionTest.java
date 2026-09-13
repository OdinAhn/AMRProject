package com.amrproject.controller;

import com.amrproject.domain.entity.event.OutboxEvent;
import com.amrproject.domain.entity.robot.Robot;
import com.amrproject.domain.entity.robot.RobotLifecycleStatus;
import com.amrproject.domain.entity.site.StationType;
import com.amrproject.domain.entity.task.TaskAssignmentStatus;
import com.amrproject.dto.request.robot.CreateRobotRequest;
import com.amrproject.dto.request.robot.UpdateRobotLifecycleStatusRequest;
import com.amrproject.dto.request.site.CreateSiteRequest;
import com.amrproject.dto.request.site.CreateStationRequest;
import com.amrproject.dto.request.task.CreateTaskRequest;
import com.amrproject.exception.InvalidStateTransitionException;
import com.amrproject.repository.event.OutboxEventRepository;
import com.amrproject.repository.robot.RobotRepository;
import com.amrproject.repository.robot.RobotStateSnapshotRepository;
import com.amrproject.repository.task.TaskAssignmentRepository;
import com.amrproject.service.event.DataRetentionService;
import com.amrproject.service.task.TaskService;
import com.amrproject.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 31단계: 동시 배정 잠금, 목록 페이지네이션, 상태 이력 개수 제한, 데이터 보관 정책을 검증한다.
 */
@AutoConfigureMockMvc
class ConcurrencyAndRetentionTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskAssignmentRepository taskAssignmentRepository;

    @Autowired
    private RobotRepository robotRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private RobotStateSnapshotRepository robotStateSnapshotRepository;

    @Autowired
    private DataRetentionService dataRetentionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 같은_로봇을_두_작업에_동시에_배정하면_하나만_성공한다() throws Exception {
        Long siteId = createSite("SITE-CC1", "동시성센터1");
        Long originId = createStation(siteId, "ST-CC1", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-CC2", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-CC1", "동시성로봇1");
        setInService(robotId);
        Long firstTaskId = createTask(originId, destinationId, "idem-cc-1");
        Long secondTaskId = createTask(originId, destinationId, "idem-cc-2");

        List<Throwable> failures = runConcurrently(
                () -> taskService.assignAndStart(firstTaskId, robotId),
                () -> taskService.assignAndStart(secondTaskId, robotId)
        );

        assertThat(failures).hasSize(1);
        assertThat(failures.getFirst()).isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("이미 다른 작업에 배정된 로봇");
        assertThat(taskAssignmentRepository.findByRobotIdAndStatus(robotId, TaskAssignmentStatus.ACTIVE)).isPresent();
    }

    @Test
    void 같은_작업을_두_로봇에_동시에_배정하면_하나만_성공한다() throws Exception {
        Long siteId = createSite("SITE-CC2", "동시성센터2");
        Long originId = createStation(siteId, "ST-CC3", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-CC4", "출고", StationType.DROPOFF);
        Long firstRobotId = createRobot(siteId, "ROBOT-CC2", "동시성로봇2");
        Long secondRobotId = createRobot(siteId, "ROBOT-CC3", "동시성로봇3");
        setInService(firstRobotId);
        setInService(secondRobotId);
        Long taskId = createTask(originId, destinationId, "idem-cc-3");

        List<Throwable> failures = runConcurrently(
                () -> taskService.assignAndStart(taskId, firstRobotId),
                () -> taskService.assignAndStart(taskId, secondRobotId)
        );

        assertThat(failures).hasSize(1);
        assertThat(failures.getFirst()).isInstanceOf(InvalidStateTransitionException.class);
        long activeAssignments = List.of(firstRobotId, secondRobotId).stream()
                .filter(id -> taskAssignmentRepository.findByRobotIdAndStatus(id, TaskAssignmentStatus.ACTIVE).isPresent())
                .count();
        assertThat(activeAssignments).isEqualTo(1);
    }

    @Test
    void 작업_목록은_최신순_페이지로_조회된다() throws Exception {
        Long siteId = createSite("SITE-PG1", "페이지센터1");
        Long originId = createStation(siteId, "ST-PG1", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-PG2", "출고", StationType.DROPOFF);
        createTask(originId, destinationId, "idem-pg-1");
        createTask(originId, destinationId, "idem-pg-2");
        Long newestTaskId = createTask(originId, destinationId, "idem-pg-3");

        mockMvc.perform(get("/api/admin/tasks").param("siteId", String.valueOf(siteId)).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(newestTaskId))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/admin/tasks").param("siteId", String.valueOf(siteId)).param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        // 범위를 벗어난 size는 1~100으로 맞춘다.
        mockMvc.perform(get("/api/admin/alarms").param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void 보관_기간이_지난_발행완료_이벤트와_상태이력만_지우고_상태이력_조회는_개수를_제한한다() throws Exception {
        Long siteId = createSite("SITE-RT1", "보관센터1");
        Long originId = createStation(siteId, "ST-RT1", "입고", StationType.PICKUP);
        Long destinationId = createStation(siteId, "ST-RT2", "출고", StationType.DROPOFF);
        Long robotId = createRobot(siteId, "ROBOT-RT1", "보관로봇1");
        setInService(robotId);
        Long taskId = createTask(originId, destinationId, "idem-rt-1");
        taskService.assignAndStart(taskId, robotId);
        Robot robot = robotRepository.findById(robotId).orElseThrow();
        assertThat(robotStateSnapshotRepository.findByRobotIdOrderByReportedAtDesc(robotId, PageRequest.of(0, 10))).hasSizeGreaterThan(1);

        mockMvc.perform(get("/api/admin/robots/{robotId}/state-history", robotId).param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        OutboxEvent oldPublished = outboxEventRepository.save(new OutboxEvent("TASK", taskId, "task.retention_old", null));
        oldPublished.markPublished();
        outboxEventRepository.save(oldPublished);
        OutboxEvent oldPending = outboxEventRepository.save(new OutboxEvent("TASK", taskId, "task.retention_pending", null));
        OutboxEvent recentPublished = outboxEventRepository.save(new OutboxEvent("TASK", taskId, "task.retention_recent", null));
        recentPublished.markPublished();
        outboxEventRepository.save(recentPublished);
        // 시간 이동은 DB에서 직접 한다 — Java Timestamp는 JVM 시간대로 변환돼 Hibernate가 UTC로 저장한 값과 어긋난다.
        jdbcTemplate.update("update outbox_events set created_at = date_sub(created_at, interval 40 day) where id in (?, ?)",
                oldPublished.getId(), oldPending.getId());
        jdbcTemplate.update("update robot_state_snapshots set received_at = date_sub(received_at, interval 40 day) where robot_id = ?", robot.getId());

        dataRetentionService.purgeOlderThan(Instant.now().minus(30, ChronoUnit.DAYS));

        assertThat(outboxEventRepository.findById(oldPublished.getId())).isEmpty();
        assertThat(outboxEventRepository.findById(oldPending.getId())).isPresent();
        assertThat(outboxEventRepository.findById(recentPublished.getId())).isPresent();
        assertThat(robotStateSnapshotRepository.findByRobotIdOrderByReportedAtDesc(robotId, PageRequest.of(0, 10))).isEmpty();
    }

    /** 두 작업을 동시에 시작시키고, 실패한 작업의 예외 목록을 반환한다. */
    private List<Throwable> runConcurrently(Runnable first, Runnable second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Runnable runnable : List.of(first, second)) {
                futures.add(executor.submit(() -> {
                    try {
                        start.await();
                        runnable.run();
                    } catch (Throwable t) {
                        failures.add(t);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        return failures;
    }

    private void setInService(Long robotId) throws Exception {
        mockMvc.perform(patch("/api/admin/robots/{robotId}/lifecycle-status", robotId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRobotLifecycleStatusRequest(RobotLifecycleStatus.IN_SERVICE))))
                .andExpect(status().isOk());
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
}

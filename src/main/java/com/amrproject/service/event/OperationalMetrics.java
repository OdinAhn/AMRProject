package com.amrproject.service.event;

import com.amrproject.domain.entity.alarm.AlarmStatus;
import com.amrproject.domain.entity.event.OutboxEventStatus;
import com.amrproject.domain.entity.task.TaskStatus;
import com.amrproject.repository.alarm.AlarmRepository;
import com.amrproject.repository.event.OutboxEventRepository;
import com.amrproject.repository.task.TaskRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 35단계: JVM·HTTP·커넥션 풀 같은 기본 메트릭 외에, 운영자가 경보를 걸 만한 업무 지표를 게이지로 노출한다.
 * 값은 수집(scrape)할 때마다 인덱스가 있는 count 쿼리로 읽는다 — 별도 캐시나 스케줄러를 두지 않는다.
 *
 * <ul>
 *   <li>{@code amr_outbox_events_pending}: 계속 늘면 Kafka/Redis 발행이 막힌 것이다(실시간 화면·이벤트 로그 지연).</li>
 *   <li>{@code amr_tasks{status}}: 대기·진행·복구 필요 작업 수. 복구 필요가 쌓이면 사람이 개입해야 한다.</li>
 *   <li>{@code amr_alarms_open}: 미확인 알람 수.</li>
 * </ul>
 */
@Component
public class OperationalMetrics implements MeterBinder {

    private static final List<TaskStatus> TRACKED_TASK_STATUSES = List.of(
            TaskStatus.QUEUED, TaskStatus.RUNNING, TaskStatus.PAUSED, TaskStatus.RECOVERY_REQUIRED
    );

    private final OutboxEventRepository outboxEventRepository;
    private final TaskRepository taskRepository;
    private final AlarmRepository alarmRepository;

    public OperationalMetrics(
            OutboxEventRepository outboxEventRepository,
            TaskRepository taskRepository,
            AlarmRepository alarmRepository
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.taskRepository = taskRepository;
        this.alarmRepository = alarmRepository;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("amr.outbox.events.pending", () -> outboxEventRepository.countByStatus(OutboxEventStatus.PENDING))
                .description("발행 대기(PENDING) 아웃박스 이벤트 수")
                .register(registry);
        for (TaskStatus status : TRACKED_TASK_STATUSES) {
            Gauge.builder("amr.tasks", () -> taskRepository.countByStatus(status))
                    .description("상태별 작업 수")
                    .tag("status", status.name())
                    .register(registry);
        }
        Gauge.builder("amr.alarms.open", () -> alarmRepository.countByStatus(AlarmStatus.OPEN))
                .description("미확인(OPEN) 알람 수")
                .register(registry);
    }
}

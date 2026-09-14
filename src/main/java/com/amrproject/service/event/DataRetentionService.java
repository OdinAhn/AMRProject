package com.amrproject.service.event;

import com.amrproject.repository.event.AuditLogRepository;
import com.amrproject.repository.event.OutboxEventRepository;
import com.amrproject.repository.robot.RobotStateSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.function.IntSupplier;

/**
 * 31단계: 계속 쌓이기만 하던 이력성 테이블의 보관 정책. 하루 한 번(기본 새벽 3시 30분) 보관 기간
 * ({@code app.retention.days}, 기본 30일)이 지난 행을 지운다.
 * <ul>
 *   <li>{@code outbox_events}: 발행 완료(PUBLISHED)된 것만. PENDING은 아직 전달되지 않았으므로 절대 지우지 않는다.
 *       지운 이벤트는 SSE {@code Last-Event-ID} 재접속 백로그로 재생할 수 없다 — 그보다 오래 끊겼던 클라이언트는 목록을 다시 조회한다.
 *       Kafka 토픽(내구성 있는 로그)에는 남아 있다.</li>
 *   <li>{@code robot_state_snapshots}: 서버 수신 시각 기준.</li>
 * </ul>
 * {@link #BATCH_SIZE}건씩 별도 트랜잭션으로 지워, 한 번에 대량 삭제하며 테이블을 오래 잠그지 않는다.
 * {@code task_events}·{@code command_events}·{@code alarm_events}는 감사 추적 성격이라 이번 범위에서 지우지 않는다.
 *
 * <p>42단계: {@code audit_logs}(37단계)는 운영 이력보다 오래 두어야 해 별도 기간({@code app.retention.audit-days}, 기본 365일)으로 지운다.
 * 0 이하면 지우지 않는다(무기한 보관).
 */
@Service
public class DataRetentionService {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionService.class);
    static final int BATCH_SIZE = 1000;

    private final OutboxEventRepository outboxEventRepository;
    private final RobotStateSnapshotRepository robotStateSnapshotRepository;
    private final AuditLogRepository auditLogRepository;
    private final TransactionTemplate transactionTemplate;
    private final boolean enabled;
    private final Duration retention;
    private final long auditRetentionDays;

    public DataRetentionService(
            OutboxEventRepository outboxEventRepository,
            RobotStateSnapshotRepository robotStateSnapshotRepository,
            AuditLogRepository auditLogRepository,
            PlatformTransactionManager transactionManager,
            @Value("${app.retention.enabled:true}") boolean enabled,
            @Value("${app.retention.days:30}") long retentionDays,
            @Value("${app.retention.audit-days:365}") long auditRetentionDays
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.robotStateSnapshotRepository = robotStateSnapshotRepository;
        this.auditLogRepository = auditLogRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
        this.retention = Duration.ofDays(retentionDays);
        this.auditRetentionDays = auditRetentionDays;
    }

    @Scheduled(cron = "${app.retention.cron:0 30 3 * * *}")
    public void run() {
        if (!enabled) {
            return;
        }
        PurgeResult result = purgeOlderThan(Instant.now().minus(retention));
        if (result.outboxEvents() > 0 || result.robotStateSnapshots() > 0) {
            log.info("보관 기간({}일) 지난 데이터 삭제: outbox_events={}, robot_state_snapshots={}",
                    retention.toDays(), result.outboxEvents(), result.robotStateSnapshots());
        }
        if (auditRetentionDays > 0) {
            int auditLogs = purgeAuditLogsOlderThan(Instant.now().minus(Duration.ofDays(auditRetentionDays)));
            if (auditLogs > 0) {
                log.info("보관 기간({}일) 지난 감사 로그 삭제: audit_logs={}", auditRetentionDays, auditLogs);
            }
        }
    }

    /** 42단계: cutoff보다 오래된 감사 로그를 지우고 건수를 반환한다. */
    public int purgeAuditLogsOlderThan(Instant cutoff) {
        return deleteInBatches(() -> auditLogRepository.deleteCreatedBefore(cutoff, BATCH_SIZE));
    }

    /** cutoff보다 오래된 행을 지우고 테이블별 삭제 건수를 반환한다. */
    public PurgeResult purgeOlderThan(Instant cutoff) {
        int outbox = deleteInBatches(() -> outboxEventRepository.deletePublishedBefore(cutoff, BATCH_SIZE));
        int snapshots = deleteInBatches(() -> robotStateSnapshotRepository.deleteReceivedBefore(cutoff, BATCH_SIZE));
        return new PurgeResult(outbox, snapshots);
    }

    private int deleteInBatches(IntSupplier deleteBatch) {
        int total = 0;
        while (true) {
            Integer deleted = transactionTemplate.execute(status -> deleteBatch.getAsInt());
            int count = deleted == null ? 0 : deleted;
            total += count;
            if (count < BATCH_SIZE) {
                return total;
            }
        }
    }

    public record PurgeResult(int outboxEvents, int robotStateSnapshots) {
    }
}

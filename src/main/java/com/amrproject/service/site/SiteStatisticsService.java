package com.amrproject.service.site;

import com.amrproject.domain.entity.robot.Robot;
import com.amrproject.domain.entity.task.TaskAssignment;
import com.amrproject.domain.entity.task.TaskAssignmentStatus;
import com.amrproject.domain.entity.task.TaskStatus;
import com.amrproject.dto.response.site.SiteStatisticsResponse;
import com.amrproject.dto.response.site.SiteStatisticsResponse.AlarmCount;
import com.amrproject.dto.response.site.SiteStatisticsResponse.DailyCount;
import com.amrproject.dto.response.site.SiteStatisticsResponse.RobotUtilization;
import com.amrproject.dto.response.site.SiteStatisticsResponse.TaskSummary;
import com.amrproject.exception.InvalidRequestException;
import com.amrproject.repository.alarm.AlarmRepository;
import com.amrproject.repository.robot.RobotRepository;
import com.amrproject.repository.task.TaskAssignmentRepository;
import com.amrproject.repository.task.TaskRepository;
import com.amrproject.repository.task.TaskRepository.CompletedTaskTiming;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Stream;

/**
 * 34단계: 물류센터 운영 통계(처리량·성공률·평균 처리/대기 시간·로봇 가동률·알람).
 *
 * <p>DB는 UTC 시각만 저장하므로, 날짜 경계는 요청한 zone으로 계산해 [from 00:00, to+1 00:00) 구간으로 조회하고
 * 일별 집계도 같은 zone으로 나눈다. 집계는 기간 안의 행만 가벼운 프로젝션으로 읽어 메모리에서 한다 —
 * 최대 92일 × 물류센터 하나 규모라 충분하고, DB별 시간대 함수(CONVERT_TZ 등)에 기대지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class SiteStatisticsService {

    static final int MAX_RANGE_DAYS = 92;
    private static final int DEFAULT_RANGE_DAYS = 7;

    private final SiteService siteService;
    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final RobotRepository robotRepository;
    private final AlarmRepository alarmRepository;

    public SiteStatisticsService(
            SiteService siteService,
            TaskRepository taskRepository,
            TaskAssignmentRepository taskAssignmentRepository,
            RobotRepository robotRepository,
            AlarmRepository alarmRepository
    ) {
        this.siteService = siteService;
        this.taskRepository = taskRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.robotRepository = robotRepository;
        this.alarmRepository = alarmRepository;
    }

    /** from/to가 null이면 zone 기준 오늘까지 최근 7일. */
    public SiteStatisticsResponse getStatistics(Long siteId, LocalDate from, LocalDate to, String zoneId) {
        siteService.getById(siteId);
        ZoneId zone = parseZone(zoneId);
        LocalDate end = to != null ? to : LocalDate.now(zone);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_RANGE_DAYS - 1);
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days < 1 || days > MAX_RANGE_DAYS) {
            throw new InvalidRequestException(
                    "STATISTICS_RANGE_INVALID",
                    "조회 기간은 시작일이 종료일보다 늦지 않고 최대 " + MAX_RANGE_DAYS + "일이어야 합니다."
            );
        }

        Instant rangeStart = start.atStartOfDay(zone).toInstant();
        Instant rangeEnd = end.plusDays(1).atStartOfDay(zone).toInstant();
        Instant now = Instant.now();

        List<CompletedTaskTiming> completed = taskRepository.findCompletedTimings(siteId, rangeStart, rangeEnd);
        List<Instant> createdAts = taskRepository.findCreatedAts(siteId, rangeStart, rangeEnd);

        return new SiteStatisticsResponse(
                siteId,
                start,
                end,
                zone.getId(),
                summarizeTasks(completed, createdAts.size()),
                daily(start, days, zone, completed, createdAts),
                robotUtilizations(siteId, rangeStart, rangeEnd, now),
                alarmRepository.countRaisedBySite(siteId, rangeStart, rangeEnd).stream()
                        .map(count -> new AlarmCount(count.getAlarmType(), count.getSeverity(), count.getCount()))
                        .sorted(Comparator.comparingLong(AlarmCount::count).reversed()
                                .thenComparing(count -> count.alarmType().name()))
                        .toList()
        );
    }

    private TaskSummary summarizeTasks(List<CompletedTaskTiming> completed, long created) {
        long succeeded = countStatus(completed, TaskStatus.SUCCEEDED);
        long failed = countStatus(completed, TaskStatus.FAILED);
        long canceled = countStatus(completed, TaskStatus.CANCELED);
        Double successRate = succeeded + failed == 0 ? null : roundOneDecimal(succeeded * 100.0 / (succeeded + failed));

        OptionalDouble leadTime = completed.stream()
                .filter(timing -> timing.getStatus() == TaskStatus.SUCCEEDED)
                .mapToLong(timing -> Duration.between(timing.getCreatedAt(), timing.getCompletedAt()).toSeconds())
                .average();
        OptionalDouble waitTime = completed.stream()
                .filter(timing -> timing.getFirstAssignedAt() != null)
                .mapToLong(timing -> Duration.between(timing.getCreatedAt(), timing.getFirstAssignedAt()).toSeconds())
                .average();

        return new TaskSummary(
                created,
                succeeded,
                failed,
                canceled,
                successRate,
                leadTime.isPresent() ? Math.round(leadTime.getAsDouble()) : null,
                waitTime.isPresent() ? Math.round(waitTime.getAsDouble()) : null
        );
    }

    private List<DailyCount> daily(LocalDate start, long days, ZoneId zone, List<CompletedTaskTiming> completed, List<Instant> createdAts) {
        Map<LocalDate, Long> created = countByDate(createdAts.stream(), zone);
        Map<LocalDate, Long> succeeded = countByDate(completedAtsWith(completed, TaskStatus.SUCCEEDED), zone);
        Map<LocalDate, Long> failed = countByDate(completedAtsWith(completed, TaskStatus.FAILED), zone);
        Map<LocalDate, Long> canceled = countByDate(completedAtsWith(completed, TaskStatus.CANCELED), zone);
        return Stream.iterate(start, date -> date.plusDays(1))
                .limit(days)
                .map(date -> new DailyCount(
                        date,
                        created.getOrDefault(date, 0L),
                        succeeded.getOrDefault(date, 0L),
                        failed.getOrDefault(date, 0L),
                        canceled.getOrDefault(date, 0L)
                ))
                .toList();
    }

    /**
     * 가동률 = 기간 중 이미 지난 시간 대비 작업이 배정돼 있던 시간. 배정은 로봇당 동시에 하나뿐이라(31단계 잠금) 겹치지 않으므로
     * 구간 길이를 그대로 더한다. 진행 중인 배정은 지금까지만 센다.
     */
    private List<RobotUtilization> robotUtilizations(Long siteId, Instant rangeStart, Instant rangeEnd, Instant now) {
        Instant observedEnd = now.isBefore(rangeEnd) ? now : rangeEnd;
        long observedSeconds = Duration.between(rangeStart, observedEnd).toSeconds();

        Map<Long, Long> busySeconds = new HashMap<>();
        Map<Long, Long> completedTasks = new HashMap<>();
        for (TaskAssignment assignment : taskAssignmentRepository.findOverlapping(siteId, rangeStart, rangeEnd)) {
            Long robotId = assignment.getRobot().getId();
            Instant released = assignment.getReleasedAt() != null ? assignment.getReleasedAt() : now;
            Instant overlapStart = assignment.getAssignedAt().isAfter(rangeStart) ? assignment.getAssignedAt() : rangeStart;
            Instant overlapEnd = released.isBefore(observedEnd) ? released : observedEnd;
            long seconds = Math.max(0, Duration.between(overlapStart, overlapEnd).toSeconds());
            busySeconds.merge(robotId, seconds, Long::sum);
            if (assignment.getStatus() == TaskAssignmentStatus.COMPLETED && assignment.getReleasedAt().isBefore(rangeEnd)) {
                completedTasks.merge(robotId, 1L, Long::sum);
            }
        }

        return robotRepository.findBySiteId(siteId).stream()
                .sorted(Comparator.comparing(Robot::getCode))
                .map(robot -> {
                    long busy = busySeconds.getOrDefault(robot.getId(), 0L);
                    return new RobotUtilization(
                            robot.getId(),
                            robot.getCode(),
                            robot.getName(),
                            completedTasks.getOrDefault(robot.getId(), 0L),
                            busy,
                            observedSeconds > 0 ? roundOneDecimal(Math.min(100.0, busy * 100.0 / observedSeconds)) : null
                    );
                })
                .toList();
    }

    private static ZoneId parseZone(String zoneId) {
        try {
            return ZoneId.of(zoneId);
        } catch (DateTimeException e) {
            throw new InvalidRequestException("STATISTICS_ZONE_INVALID", "알 수 없는 시간대입니다: " + zoneId);
        }
    }

    private static long countStatus(List<CompletedTaskTiming> completed, TaskStatus status) {
        return completed.stream().filter(timing -> timing.getStatus() == status).count();
    }

    private static Stream<Instant> completedAtsWith(List<CompletedTaskTiming> completed, TaskStatus status) {
        return completed.stream().filter(timing -> timing.getStatus() == status).map(CompletedTaskTiming::getCompletedAt);
    }

    private static Map<LocalDate, Long> countByDate(Stream<Instant> instants, ZoneId zone) {
        Map<LocalDate, Long> counts = new HashMap<>();
        instants.forEach(instant -> counts.merge(LocalDate.ofInstant(instant, zone), 1L, Long::sum));
        return counts;
    }

    private static double roundOneDecimal(double value) {
        return Math.round(value * 10) / 10.0;
    }
}

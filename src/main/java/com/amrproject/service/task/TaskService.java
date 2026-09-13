package com.amrproject.service.task;

import com.amrproject.domain.entity.alarm.AlarmSeverity;
import com.amrproject.domain.entity.alarm.AlarmSourceType;
import com.amrproject.domain.entity.alarm.AlarmType;
import com.amrproject.domain.entity.event.OutboxEvent;
import com.amrproject.domain.entity.robot.ChargingSession;
import com.amrproject.domain.entity.robot.Robot;
import com.amrproject.domain.entity.robot.RobotCommand;
import com.amrproject.domain.entity.robot.RobotCommandStatus;
import com.amrproject.domain.entity.robot.RobotCommandType;
import com.amrproject.domain.entity.robot.RobotCurrentState;
import com.amrproject.domain.entity.site.Site;
import com.amrproject.domain.entity.site.Station;
import com.amrproject.domain.entity.task.ResourceReservation;
import com.amrproject.domain.entity.task.ResourceReservationStatus;
import com.amrproject.domain.entity.task.ResourceType;
import com.amrproject.domain.entity.task.Task;
import com.amrproject.domain.entity.task.TaskAssignment;
import com.amrproject.domain.entity.task.TaskAssignmentStatus;
import com.amrproject.domain.entity.task.TaskEvent;
import com.amrproject.domain.entity.task.TaskStatus;
import com.amrproject.domain.entity.task.TaskStep;
import com.amrproject.domain.entity.task.TaskStepStatus;
import com.amrproject.domain.entity.task.TaskStepType;
import com.amrproject.domain.entity.task.TaskType;
import com.amrproject.domain.entity.user.User;
import com.amrproject.dto.request.task.CreateExternalTaskRequest;
import com.amrproject.dto.request.task.CreateTaskRequest;
import com.amrproject.dto.request.task.RecoveryResolution;
import com.amrproject.exception.EntityNotFoundException;
import com.amrproject.exception.InvalidRequestException;
import com.amrproject.exception.InvalidStateTransitionException;
import com.amrproject.repository.event.OutboxEventRepository;
import com.amrproject.repository.robot.ChargingSessionRepository;
import com.amrproject.repository.robot.RobotCommandRepository;
import com.amrproject.repository.robot.RobotCurrentStateRepository;
import com.amrproject.repository.robot.RobotRepository;
import com.amrproject.repository.site.StationRepository;
import com.amrproject.repository.task.ResourceReservationRepository;
import com.amrproject.repository.task.TaskAssignmentRepository;
import com.amrproject.repository.task.TaskEventRepository;
import com.amrproject.repository.task.TaskRepository;
import com.amrproject.repository.task.TaskStepRepository;
import com.amrproject.robot.RobotGateway;
import com.amrproject.service.alarm.AlarmService;
import com.amrproject.service.robot.RobotService;
import com.amrproject.service.site.StationService;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 작업 생성·조회·취소와 상태 전이 기록(TaskEvent)·Outbox 기록을 담당한다.
 * 로봇 배정(assignRobot/assignAndStart)은 실제 자동 배정 알고리즘(7단계)이 없어
 * "지정 배정"만 제공한다 (관리자가 robotId를 지정).
 * MOVE 타입 단계가 시작되면 {@link RobotGateway}로 명령을 디스패치하고
 * 결과에 따라 단계/작업을 진행시킨다 (4단계: 실제 장비 없이 이동 실행).
 */
@Service
@Transactional(readOnly = true)
public class TaskService {

    private static final List<TaskStepType> MOVE_STEP_TYPES = List.of(
            TaskStepType.MOVE_TO_ORIGIN,
            TaskStepType.MOVE_TO_DESTINATION,
            TaskStepType.MOVE_TO_CHARGING_STATION
    );

    /** alarms.message 컬럼 길이(V9__alarms.sql) */
    private static final int ALARM_MESSAGE_MAX_LENGTH = 500;

    private final TaskRepository taskRepository;
    private final TaskStepRepository taskStepRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final TaskEventRepository taskEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final RobotCommandRepository robotCommandRepository;
    private final ResourceReservationRepository resourceReservationRepository;
    private final ChargingSessionRepository chargingSessionRepository;
    private final RobotCurrentStateRepository robotCurrentStateRepository;
    private final StationService stationService;
    private final RobotService robotService;
    private final RobotGateway robotGateway;
    private final AlarmService alarmService;
    private final RobotRepository robotRepository;
    private final StationRepository stationRepository;

    public TaskService(
            TaskRepository taskRepository,
            TaskStepRepository taskStepRepository,
            TaskAssignmentRepository taskAssignmentRepository,
            TaskEventRepository taskEventRepository,
            OutboxEventRepository outboxEventRepository,
            RobotCommandRepository robotCommandRepository,
            ResourceReservationRepository resourceReservationRepository,
            ChargingSessionRepository chargingSessionRepository,
            RobotCurrentStateRepository robotCurrentStateRepository,
            StationService stationService,
            RobotService robotService,
            RobotGateway robotGateway,
            AlarmService alarmService,
            RobotRepository robotRepository,
            StationRepository stationRepository
    ) {
        this.taskRepository = taskRepository;
        this.taskStepRepository = taskStepRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.taskEventRepository = taskEventRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.robotCommandRepository = robotCommandRepository;
        this.resourceReservationRepository = resourceReservationRepository;
        this.chargingSessionRepository = chargingSessionRepository;
        this.robotCurrentStateRepository = robotCurrentStateRepository;
        this.stationService = stationService;
        this.robotService = robotService;
        this.robotGateway = robotGateway;
        this.alarmService = alarmService;
        this.robotRepository = robotRepository;
        this.stationRepository = stationRepository;
    }

    @Transactional
    public TaskCreationResult createTask(CreateTaskRequest request, String idempotencyKey, User requestedBy) {
        if (idempotencyKey != null) {
            Optional<Task> existing = taskRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return new TaskCreationResult(existing.get(), false);
            }
        }
        return createTransportTask(request.originStationId(), request.destinationStationId(), requestedBy, idempotencyKey, null);
    }

    /**
     * 외부 시스템 작업 접수 (9단계, API.md §6). 작업자 API와 달리 Idempotency-Key
     * 헤더가 필수가 아니다 — 호출자의 externalRef 자체가 멱등키 역할을 할 수 있어
     * 둘 중 하나만 있으면 된다.
     */
    @Transactional
    public TaskCreationResult createExternalTask(CreateExternalTaskRequest request, String idempotencyKey) {
        if (idempotencyKey == null && request.externalRef() == null) {
            throw new InvalidRequestException(
                    "EXTERNAL_TASK_KEY_REQUIRED", "Idempotency-Key 헤더 또는 externalRef 중 하나는 필수입니다."
            );
        }
        if (idempotencyKey != null) {
            Optional<Task> existing = taskRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return new TaskCreationResult(existing.get(), false);
            }
        }
        if (request.externalRef() != null) {
            Optional<Task> existing = taskRepository.findByExternalRef(request.externalRef());
            if (existing.isPresent()) {
                return new TaskCreationResult(existing.get(), false);
            }
        }
        return createTransportTask(request.originStationId(), request.destinationStationId(), null, idempotencyKey, request.externalRef());
    }

    private TaskCreationResult createTransportTask(
            Long originStationId, Long destinationStationId, User requestedBy, String idempotencyKey, String externalRef
    ) {
        Station origin = stationService.getById(originStationId);
        Station destination = stationService.getById(destinationStationId);
        Site site = origin.getSite();

        Task task = new Task(
                UUID.randomUUID().toString(),
                site,
                TaskType.TRANSPORT,
                0,
                origin,
                destination,
                requestedBy,
                idempotencyKey,
                externalRef
        );
        taskRepository.save(task);

        taskStepRepository.save(new TaskStep(task, 1, TaskStepType.MOVE_TO_ORIGIN));
        taskStepRepository.save(new TaskStep(task, 2, TaskStepType.CONFIRM_LOAD));
        taskStepRepository.save(new TaskStep(task, 3, TaskStepType.MOVE_TO_DESTINATION));
        taskStepRepository.save(new TaskStep(task, 4, TaskStepType.CONFIRM_UNLOAD));

        recordEvent(task, "CREATED", null, task.getStatus(), requestedBy);
        return new TaskCreationResult(task, true);
    }

    public Task getByExternalRef(String externalRef) {
        return taskRepository.findByExternalRef(externalRef)
                .orElseThrow(() -> new EntityNotFoundException(
                        "TASK_NOT_FOUND", "externalRef로 작업을 찾을 수 없습니다: " + externalRef
                ));
    }

    /**
     * 배터리가 낮은 로봇을 위한 자동 충전 작업 (7단계, DispatchService가 호출한다).
     * origin/destination을 모두 충전소로 둔다 — 로봇의 현재 위치를 추적하지 않아
     * "어디서 오는지"는 의미가 없고, 목적지(충전소)만 중요하다.
     */
    @Transactional
    public Task createChargeTask(Robot robot, Station chargingStation) {
        Task task = new Task(
                UUID.randomUUID().toString(),
                chargingStation.getSite(),
                TaskType.CHARGE,
                0,
                chargingStation,
                chargingStation,
                null,
                null,
                null
        );
        taskRepository.save(task);

        taskStepRepository.save(new TaskStep(task, 1, TaskStepType.MOVE_TO_CHARGING_STATION));
        taskStepRepository.save(new TaskStep(task, 2, TaskStepType.DOCK_AND_CHARGE));

        recordEvent(task, "CREATED", null, task.getStatus(), null);
        return assignAndStart(task.getId(), robot.getId());
    }

    public Task getById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("TASK_NOT_FOUND", "작업을 찾을 수 없습니다: " + id));
    }

    public List<TaskStep> getSteps(Long taskId) {
        return taskStepRepository.findByTaskIdOrderByStepNoAsc(taskId);
    }

    public List<TaskEvent> getEvents(Long taskId) {
        return taskEventRepository.findByTaskIdOrderByCreatedAtAsc(taskId);
    }

    /**
     * 관리자가 로봇을 지정 배정하면 그 즉시 첫 단계를 시작한다 (5단계: STATE_MACHINES.md §1의
     * ASSIGNED→RUNNING은 "첫 명령 ACCEPTED"가 트리거이며 별도의 수동 시작 액션이 아니다).
     */
    @Transactional
    public Task assignAndStart(Long taskId, Long robotId) {
        Robot robot = robotService.getById(robotId);
        assignRobot(taskId, robot);
        return start(taskId);
    }

    /**
     * 31단계: 동시 배정 방지. 작업 행 → 로봇 행 순서로 잠근 뒤(모든 배정 경로가 같은 순서라 교착이 생기지 않는다),
     * 로봇의 진행 중 배정 여부를 잠금 조회로 다시 확인한다. 예전엔 두 요청이 동시에 "배정 없음"을 읽고 같은 로봇을
     * 두 작업에 배정하거나, 같은 작업을 두 로봇에 배정할 수 있었다.
     */
    @Transactional
    public TaskAssignment assignRobot(Long taskId, Robot robot) {
        Task task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new EntityNotFoundException("TASK_NOT_FOUND", "작업을 찾을 수 없습니다: " + taskId));
        robotRepository.findByIdForUpdate(robot.getId())
                .orElseThrow(() -> new EntityNotFoundException("ROBOT_NOT_FOUND", "로봇을 찾을 수 없습니다: " + robot.getId()));
        if (taskAssignmentRepository.findByRobotIdAndStatusForUpdate(robot.getId(), TaskAssignmentStatus.ACTIVE).isPresent()) {
            throw new InvalidStateTransitionException(
                    "ROBOT_ALREADY_ASSIGNED",
                    "이미 다른 작업에 배정된 로봇입니다: " + robot.getId()
            );
        }
        TaskStatus previous = task.getStatus();
        TaskAssignment assignment = new TaskAssignment(task, robot);
        taskAssignmentRepository.save(assignment);
        task.assign(assignment);
        recordEvent(task, "ASSIGNED", previous, task.getStatus(), null);
        return assignment;
    }

    @Transactional
    public Task start(Long taskId) {
        Task task = getById(taskId);
        TaskStatus previous = task.getStatus();
        task.start();
        recordEvent(task, "STARTED", previous, task.getStatus(), null);

        TaskStep firstStep = taskStepRepository.findByTaskIdAndStepNo(taskId, 1)
                .orElseThrow(() -> new InvalidStateTransitionException("NO_FIRST_STEP", "1번 단계가 없습니다."));
        advanceToStep(task, firstStep);
        return task;
    }

    /**
     * 현재 RUNNING인 단계를 완료 처리하고 다음 단계를 시작한다.
     * 마지막 단계면 작업을 SUCCEEDED로 전이한다.
     * CONFIRM_LOAD/CONFIRM_UNLOAD처럼 로봇 명령이 없는 단계는 작업자 버튼(5단계)이
     * 이 메서드를 호출하게 된다. MOVE 단계는 시뮬레이터가 자동으로 완료시킨다.
     */
    @Transactional
    public Task completeCurrentStep(Long taskId) {
        Task task = getById(taskId);
        TaskStep current = findRunningStep(taskId);
        completeStepInternal(task, current);
        return task;
    }

    /**
     * 작업자 '로봇 보내기' 버튼 (API.md §4). CONFIRM_LOAD 단계 완료 처리.
     * 이미 지나간 단계에 대한 재요청은 409 대신 현재 상태를 그대로 반환한다 (멱등).
     */
    @Transactional
    public Task dispatchAfterLoad(Long taskId) {
        return completeNamedStep(taskId, TaskStepType.CONFIRM_LOAD, "TASK_DISPATCH");
    }

    /**
     * 작업자 '하역 완료' 버튼 (API.md §4). CONFIRM_UNLOAD 단계 완료 처리.
     */
    @Transactional
    public Task confirmUnload(Long taskId) {
        return completeNamedStep(taskId, TaskStepType.CONFIRM_UNLOAD, "TASK_CONFIRM_UNLOAD");
    }

    private Task completeNamedStep(Long taskId, TaskStepType expectedType, String actionCode) {
        Task task = getById(taskId);
        TaskStep step = taskStepRepository.findByTaskIdOrderByStepNoAsc(taskId).stream()
                .filter(s -> s.getStepType() == expectedType)
                .findFirst()
                .orElseThrow(() -> new InvalidStateTransitionException(
                        actionCode + "_NOT_ALLOWED", expectedType + " 단계가 없는 작업입니다."
                ));
        if (step.getStatus() == TaskStepStatus.SUCCEEDED) {
            return task;
        }
        if (step.getStatus() != TaskStepStatus.RUNNING) {
            throw new InvalidStateTransitionException(
                    actionCode + "_NOT_ALLOWED",
                    expectedType + " 단계가 진행 중이 아니어서(현재: " + step.getStatus() + ") 처리할 수 없습니다."
            );
        }
        completeStepInternal(task, step);
        return task;
    }

    /**
     * 관리자 복구 개입 (API.md §3). RESUME은 실패/불확실 단계를 재시도하고, FAIL은
     * 작업을 최종 실패 처리하며 배정을 해제한다. 이미 처리된 요청은 그대로 현재 상태를 반환한다 (멱등).
     */
    @Transactional
    public Task recover(Long taskId, RecoveryResolution resolution) {
        Task task = getById(taskId);
        if (resolution == RecoveryResolution.FAIL) {
            if (task.getStatus() == TaskStatus.FAILED) {
                return task;
            }
            return failRecovery(task);
        }
        if (task.getStatus() == TaskStatus.RUNNING) {
            return task;
        }
        return resumeRecovery(task);
    }

    private Task resumeRecovery(Task task) {
        TaskStep step = findRecoveryStep(task.getId());
        if (step.getStatus() == TaskStepStatus.FAILED) {
            step.retry();
        }
        TaskStatus previous = task.getStatus();
        task.resumeFromRecovery();
        recordEvent(task, "RECOVERY_RESUMED", previous, task.getStatus(), null);
        // 재발행한 명령이 다시 실패하면 markRecoveryRequired가 새 알람을 올리므로, 명령 발행 전에 먼저 해소한다.
        alarmService.autoResolve(AlarmType.TASK_RECOVERY_REQUIRED, AlarmSourceType.TASK, task.getId());
        // CONFIRM_LOAD/CONFIRM_UNLOAD처럼 명령이 없는 단계(8단계: 적재 중 취소 복구)는
        // 재발행할 명령이 없다 — 작업자가 원래 버튼(dispatch/confirm-unload)으로 이어간다.
        if (MOVE_STEP_TYPES.contains(step.getStepType()) || step.getStepType() == TaskStepType.DOCK_AND_CHARGE) {
            dispatchStepCommand(task, step);
        }
        return task;
    }

    private Task failRecovery(Task task) {
        TaskStep step = findRecoveryStep(task.getId());
        if (step.getStatus() == TaskStepStatus.RUNNING) {
            step.fail();
        }
        TaskStatus previous = task.getStatus();
        task.failFromRecovery();
        recordEvent(task, "RECOVERY_FAILED", previous, task.getStatus(), null);
        alarmService.autoResolve(AlarmType.TASK_RECOVERY_REQUIRED, AlarmSourceType.TASK, task.getId());
        alarmService.raise(AlarmType.TASK_FAILED, AlarmSeverity.CRITICAL, AlarmSourceType.TASK, task.getId(),
                "작업이 복구 불가로 최종 실패 처리되었습니다: " + task.getTaskNo());
        releaseAssignment(task);
        releaseReservations(task.getCurrentAssignment().getRobot());
        return task;
    }

    private TaskStep findRecoveryStep(Long taskId) {
        return taskStepRepository.findByTaskIdOrderByStepNoAsc(taskId).stream()
                .filter(step -> step.getStatus() == TaskStepStatus.RUNNING || step.getStatus() == TaskStepStatus.FAILED)
                .findFirst()
                .orElseThrow(() -> new InvalidStateTransitionException(
                        "NO_RECOVERY_STEP", "복구 대상 단계가 없습니다."
                ));
    }

    private void releaseAssignment(Task task) {
        TaskAssignment assignment = task.getCurrentAssignment();
        if (assignment != null && assignment.getStatus() == TaskAssignmentStatus.ACTIVE) {
            assignment.release();
        }
    }

    /**
     * 취소 요청을 끝까지 처리한다 (8단계). 이 시스템은 전부 동기 시뮬레이터라 서버가
     * 명령을 보내는 순간 로봇은 이미 그 명령을 끝낸 상태다 — "취소 명령을 보내고
     * 응답을 기다리는" 절차가 의미가 없다. 대신 적재 여부로 나눈다: 적재 전이거나
     * 이미 하역까지 끝났으면 즉시 CANCELED로 확정, 적재 중(물건이 로봇에 있음)이면
     * 임의로 취소를 확정하지 않고 RECOVERY_REQUIRED로 보내 운영자가 recover로 결정한다.
     */
    @Transactional
    public Task requestCancel(Long taskId, User actor) {
        Task task = getById(taskId);
        TaskStatus previous = task.getStatus();
        task.requestCancel();
        if (task.getStatus() == previous) {
            return task;
        }
        recordEvent(task, "CANCEL_REQUESTED", previous, task.getStatus(), actor);
        if (task.getStatus() == TaskStatus.CANCEL_REQUESTED) {
            resolveCancelRequest(task);
        }
        return task;
    }

    private void resolveCancelRequest(Task task) {
        if (isCarryingLoad(task)) {
            markRecoveryRequired(task, "RECOVERY_REQUIRED", null, "적재 중 취소 요청");
            return;
        }
        TaskStatus previous = task.getStatus();
        task.confirmCanceled();
        recordEvent(task, "CANCELED", previous, task.getStatus(), null);
        releaseAssignment(task);
        releaseReservations(task.getCurrentAssignment().getRobot());
    }

    private boolean isCarryingLoad(Task task) {
        boolean loaded = false;
        for (TaskStep step : taskStepRepository.findByTaskIdOrderByStepNoAsc(task.getId())) {
            if (step.getStepType() == TaskStepType.CONFIRM_LOAD && step.getStatus() == TaskStepStatus.SUCCEEDED) {
                loaded = true;
            }
            if (step.getStepType() == TaskStepType.CONFIRM_UNLOAD && step.getStatus() == TaskStepStatus.SUCCEEDED) {
                loaded = false;
            }
        }
        return loaded;
    }

    /**
     * 작업자 '작업 불가' 버튼 (API.md §4). 아직 배정 전이면 그냥 취소하고, 배정된
     * 상태면 취소와 동일하게 개입이 필요한 상태로 보낸다 — 사유는 이벤트에 남긴다.
     */
    @Transactional
    public Task reportUnavailable(Long taskId, String reason) {
        Task task = getById(taskId);
        TaskStatus status = task.getStatus();
        if (status == TaskStatus.QUEUED) {
            task.requestCancel();
            recordEvent(task, "REPORT_UNAVAILABLE", status, task.getStatus(), null, reason);
            return task;
        }
        if (status == TaskStatus.ASSIGNED || status == TaskStatus.RUNNING || status == TaskStatus.PAUSED) {
            markRecoveryRequired(task, "REPORT_UNAVAILABLE", reason, "작업자 작업 불가 보고: " + reason);
            return task;
        }
        throw new InvalidStateTransitionException(
                "TASK_REPORT_UNAVAILABLE_NOT_ALLOWED",
                "현재 상태(" + status + ")에서는 작업 불가를 보고할 수 없습니다."
        );
    }

    /**
     * 관리자 작업 목록 조회 (API.md §3, 8단계: 재접속 대조 — "지금 개입이 필요한
     * 작업이 뭔지" status로 걸러 조회). station/robot/기간 필터는 아직 미구현.
     * 31단계: 페이지로 조회한다(예전엔 전체 목록).
     */
    public Page<Task> getPage(Long siteId, TaskStatus status, Pageable pageable) {
        return taskRepository.search(siteId, status, pageable);
    }

    /** 31단계: 여러 작업의 단계를 한 번에 읽어 작업 id별로 묶는다(목록 응답의 N+1 제거). */
    public Map<Long, List<TaskStep>> getStepsByTaskIds(Collection<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        return taskStepRepository.findByTaskIdInOrderByTaskIdAscStepNoAsc(taskIds).stream()
                .collect(Collectors.groupingBy(step -> step.getTask().getId(), LinkedHashMap::new, Collectors.toList()));
    }

    private static final Set<TaskStatus> TERMINAL_STATUSES = Set.of(
            TaskStatus.SUCCEEDED, TaskStatus.CANCELED, TaskStatus.FAILED
    );

    /**
     * 작업자 화면(12단계): 해당 station이 출발/도착 어느 쪽이든 관련 있고 아직
     * 종료되지 않은 작업만 반환한다 (API.md §4 "진행 중/대기 작업").
     */
    public List<Task> getActiveByStation(Long stationId) {
        stationService.getById(stationId);
        return taskRepository.findActiveByStation(stationId, TERMINAL_STATUSES);
    }

    private TaskStep findRunningStep(Long taskId) {
        return taskStepRepository.findByTaskIdOrderByStepNoAsc(taskId).stream()
                .filter(step -> step.getStatus() == TaskStepStatus.RUNNING)
                .findFirst()
                .orElseThrow(() -> new InvalidStateTransitionException("NO_RUNNING_STEP", "진행 중인 단계가 없습니다."));
    }

    private static final List<TaskStepType> STATION_RELEASE_STEP_TYPES = List.of(
            TaskStepType.CONFIRM_LOAD, TaskStepType.CONFIRM_UNLOAD, TaskStepType.DOCK_AND_CHARGE
    );

    private void completeStepInternal(Task task, TaskStep current) {
        current.succeed();
        if (STATION_RELEASE_STEP_TYPES.contains(current.getStepType())) {
            releaseReservations(task.getCurrentAssignment().getRobot());
        }
        List<TaskStep> steps = taskStepRepository.findByTaskIdOrderByStepNoAsc(task.getId());
        Optional<TaskStep> next = steps.stream()
                .filter(step -> step.getStepNo().equals(current.getStepNo() + 1))
                .findFirst();

        if (next.isPresent()) {
            advanceToStep(task, next.get());
        } else {
            TaskStatus previous = task.getStatus();
            task.succeed();
            recordEvent(task, "SUCCEEDED", previous, task.getStatus(), null);
            TaskAssignment assignment = task.getCurrentAssignment();
            if (assignment != null && assignment.getStatus() == TaskAssignmentStatus.ACTIVE) {
                assignment.complete();
            }
        }
    }

    private void advanceToStep(Task task, TaskStep step) {
        step.start();
        // 28단계: 단계 진행(로봇 보내기·하역 완료 등)은 작업 상태가 RUNNING 그대로라 task_events가 남지 않아
        // 실시간 이벤트도 없었다 — 화면이 "적재 확인 대기"에 멈춰 있지 않도록 outbox에만 단계 시작을 기록한다.
        // task_events(상태 전이 이력)는 그대로 상태 전이만 담는다.
        outboxEventRepository.save(new OutboxEvent("TASK", task.getId(), "task.step_started", step.getStepType().name()));
        if (MOVE_STEP_TYPES.contains(step.getStepType()) || step.getStepType() == TaskStepType.DOCK_AND_CHARGE) {
            dispatchStepCommand(task, step);
        }
    }

    /**
     * MOVE 단계와 DOCK_AND_CHARGE 단계 모두 로봇 명령을 발행한다 (7단계: 충전도
     * 기존 명령/게이트웨이 구조를 재사용). 명령 발행 전 목적지 station을 예약해
     * 같은 station으로 동시에 여러 로봇이 몰리는 것을 막는다 — 예약은 이 단계
     * 완료 시가 아니라, 그 station에서 로봇이 실제로 볼일을 마치는 다음 확인
     * 단계(CONFIRM_LOAD/CONFIRM_UNLOAD/DOCK_AND_CHARGE)가 끝날 때 해제된다.
     */
    private void dispatchStepCommand(Task task, TaskStep step) {
        Robot robot = task.getCurrentAssignment().getRobot();
        Station targetStation = resolveTargetStation(task, step);
        if (targetStation != null) {
            reserveStation(robot, targetStation, step);
        }

        boolean isCharging = step.getStepType() == TaskStepType.DOCK_AND_CHARGE;
        RobotCommandType commandType = isCharging ? RobotCommandType.START_CHARGE : RobotCommandType.MOVE_TO;
        RobotCommand command = new RobotCommand(UUID.randomUUID().toString(), step, robot, commandType, null);
        robotCommandRepository.save(command);

        ChargingSession session = null;
        if (isCharging) {
            RobotCurrentState state = currentStateOf(robot);
            session = chargingSessionRepository.save(new ChargingSession(robot, task.getDestinationStation(), task, state.getBatteryPct()));
            session.startCharging();
        }

        robotGateway.dispatch(command);

        if (session != null) {
            if (command.getStatus() == RobotCommandStatus.SUCCEEDED) {
                session.complete(currentStateOf(robot).getBatteryPct());
            } else {
                session.abort();
            }
        }

        reactToCommandOutcome(task, step, command);
    }

    private Station resolveTargetStation(Task task, TaskStep step) {
        return switch (step.getStepType()) {
            case MOVE_TO_ORIGIN -> task.getOriginStation();
            case MOVE_TO_DESTINATION, MOVE_TO_CHARGING_STATION -> task.getDestinationStation();
            default -> null;
        };
    }

    private void reserveStation(Robot robot, Station station, TaskStep step) {
        // 31단계: 작업장 행을 잠그고 최신 설정·예약 수로 검사한다 — 동시에 두 로봇이 마지막 한 자리를 차지하는 것을 막는다.
        Station locked = stationRepository.findByIdForUpdate(station.getId()).orElse(station);
        Integer capacity = locked.getMaxConcurrentRobots();
        if (capacity != null) {
            int held = resourceReservationRepository.countHeldForShare(ResourceType.STATION.name(), station.getId());
            if (held >= capacity) {
                throw new InvalidStateTransitionException(
                        "STATION_AT_CAPACITY",
                        "station(" + station.getId() + ")이 이미 최대 동시 로봇 수(" + capacity + ")에 도달했습니다."
                );
            }
        }
        resourceReservationRepository.save(new ResourceReservation(ResourceType.STATION, station.getId(), robot, step));
    }

    private void releaseReservations(Robot robot) {
        resourceReservationRepository.findByRobotIdAndStatus(robot.getId(), ResourceReservationStatus.HELD)
                .forEach(ResourceReservation::release);
    }

    private RobotCurrentState currentStateOf(Robot robot) {
        return robotCurrentStateRepository.findById(robot.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "ROBOT_STATE_NOT_FOUND", "아직 보고된 상태가 없는 로봇입니다: " + robot.getId()
                ));
    }

    private void reactToCommandOutcome(Task task, TaskStep step, RobotCommand command) {
        RobotCommandStatus status = command.getStatus();
        if (status == RobotCommandStatus.SUCCEEDED) {
            completeStepInternal(task, step);
            return;
        }
        if (status == RobotCommandStatus.FAILED) {
            step.fail();
        }
        // FAILED, UNKNOWN 모두 "실제 상태를 알 수 없거나 실패" → 운영자/복구 로직 개입 필요
        String cause = status == RobotCommandStatus.FAILED ? "로봇 명령 실패" : "로봇 명령 결과 불명";
        markRecoveryRequired(task, "RECOVERY_REQUIRED", null, cause);
    }

    /**
     * 27단계: RECOVERY_REQUIRED로 가는 모든 경로(명령 실패·불명, 적재 중 취소, 작업 불가 보고)가
     * 이 메서드를 거쳐 TASK_RECOVERY_REQUIRED 알람을 함께 올린다 — 이전엔 작업 상태만 바뀌고
     * 관리자에게 알릴 신호가 없어 작업 목록을 상태 필터로 직접 뒤져야 했다.
     * 알람은 recover(RESUME/FAIL) 시 자동 해소된다.
     */
    private void markRecoveryRequired(Task task, String eventType, String eventPayload, String cause) {
        TaskStatus previous = task.getStatus();
        task.markRecoveryRequired();
        recordEvent(task, eventType, previous, task.getStatus(), null, eventPayload);
        alarmService.raise(AlarmType.TASK_RECOVERY_REQUIRED, AlarmSeverity.WARNING, AlarmSourceType.TASK, task.getId(),
                truncate("작업 복구가 필요합니다(" + cause + "): " + task.getTaskNo(), ALARM_MESSAGE_MAX_LENGTH));
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private void recordEvent(Task task, String eventType, TaskStatus from, TaskStatus to, User actor) {
        recordEvent(task, eventType, from, to, actor, null);
    }

    private void recordEvent(Task task, String eventType, TaskStatus from, TaskStatus to, User actor, String payload) {
        taskEventRepository.save(new TaskEvent(task, eventType, from, to, actor, payload));
        outboxEventRepository.save(new OutboxEvent("TASK", task.getId(), "task." + eventType.toLowerCase(), payload));
    }
}

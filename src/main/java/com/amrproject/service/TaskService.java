package com.amrproject.service;

import com.amrproject.domain.entity.OutboxEvent;
import com.amrproject.domain.entity.Robot;
import com.amrproject.domain.entity.RobotCommand;
import com.amrproject.domain.entity.RobotCommandStatus;
import com.amrproject.domain.entity.RobotCommandType;
import com.amrproject.domain.entity.Site;
import com.amrproject.domain.entity.Station;
import com.amrproject.domain.entity.Task;
import com.amrproject.domain.entity.TaskAssignment;
import com.amrproject.domain.entity.TaskAssignmentStatus;
import com.amrproject.domain.entity.TaskEvent;
import com.amrproject.domain.entity.TaskStatus;
import com.amrproject.domain.entity.TaskStep;
import com.amrproject.domain.entity.TaskStepStatus;
import com.amrproject.domain.entity.TaskStepType;
import com.amrproject.domain.entity.TaskType;
import com.amrproject.domain.entity.User;
import com.amrproject.dto.request.CreateTaskRequest;
import com.amrproject.dto.request.RecoveryResolution;
import com.amrproject.exception.EntityNotFoundException;
import com.amrproject.exception.InvalidStateTransitionException;
import com.amrproject.repository.OutboxEventRepository;
import com.amrproject.repository.RobotCommandRepository;
import com.amrproject.repository.TaskAssignmentRepository;
import com.amrproject.repository.TaskEventRepository;
import com.amrproject.repository.TaskRepository;
import com.amrproject.repository.TaskStepRepository;
import com.amrproject.robot.RobotGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    private final TaskRepository taskRepository;
    private final TaskStepRepository taskStepRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final TaskEventRepository taskEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final RobotCommandRepository robotCommandRepository;
    private final StationService stationService;
    private final RobotService robotService;
    private final RobotGateway robotGateway;

    public TaskService(
            TaskRepository taskRepository,
            TaskStepRepository taskStepRepository,
            TaskAssignmentRepository taskAssignmentRepository,
            TaskEventRepository taskEventRepository,
            OutboxEventRepository outboxEventRepository,
            RobotCommandRepository robotCommandRepository,
            StationService stationService,
            RobotService robotService,
            RobotGateway robotGateway
    ) {
        this.taskRepository = taskRepository;
        this.taskStepRepository = taskStepRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.taskEventRepository = taskEventRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.robotCommandRepository = robotCommandRepository;
        this.stationService = stationService;
        this.robotService = robotService;
        this.robotGateway = robotGateway;
    }

    @Transactional
    public TaskCreationResult createTask(CreateTaskRequest request, String idempotencyKey, User requestedBy) {
        if (idempotencyKey != null) {
            Optional<Task> existing = taskRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return new TaskCreationResult(existing.get(), false);
            }
        }

        Station origin = stationService.getById(request.originStationId());
        Station destination = stationService.getById(request.destinationStationId());
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
                null
        );
        taskRepository.save(task);

        taskStepRepository.save(new TaskStep(task, 1, TaskStepType.MOVE_TO_ORIGIN));
        taskStepRepository.save(new TaskStep(task, 2, TaskStepType.CONFIRM_LOAD));
        taskStepRepository.save(new TaskStep(task, 3, TaskStepType.MOVE_TO_DESTINATION));
        taskStepRepository.save(new TaskStep(task, 4, TaskStepType.CONFIRM_UNLOAD));

        recordEvent(task, "CREATED", null, task.getStatus(), requestedBy);
        return new TaskCreationResult(task, true);
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

    @Transactional
    public TaskAssignment assignRobot(Long taskId, Robot robot) {
        Task task = getById(taskId);
        if (taskAssignmentRepository.findByRobotIdAndStatus(robot.getId(), TaskAssignmentStatus.ACTIVE).isPresent()) {
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
        dispatchMoveCommand(task, step);
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
        releaseAssignment(task);
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

    @Transactional
    public Task requestCancel(Long taskId, User actor) {
        Task task = getById(taskId);
        TaskStatus previous = task.getStatus();
        task.requestCancel();
        if (task.getStatus() != previous) {
            recordEvent(task, "CANCEL_REQUESTED", previous, task.getStatus(), actor);
        }
        return task;
    }

    private TaskStep findRunningStep(Long taskId) {
        return taskStepRepository.findByTaskIdOrderByStepNoAsc(taskId).stream()
                .filter(step -> step.getStatus() == TaskStepStatus.RUNNING)
                .findFirst()
                .orElseThrow(() -> new InvalidStateTransitionException("NO_RUNNING_STEP", "진행 중인 단계가 없습니다."));
    }

    private void completeStepInternal(Task task, TaskStep current) {
        current.succeed();
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
        if (MOVE_STEP_TYPES.contains(step.getStepType())) {
            dispatchMoveCommand(task, step);
        }
    }

    private void dispatchMoveCommand(Task task, TaskStep step) {
        Robot robot = task.getCurrentAssignment().getRobot();
        RobotCommand command = new RobotCommand(UUID.randomUUID().toString(), step, robot, RobotCommandType.MOVE_TO, null);
        robotCommandRepository.save(command);

        robotGateway.dispatch(command);

        reactToCommandOutcome(task, step, command);
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
        TaskStatus previous = task.getStatus();
        task.markRecoveryRequired();
        recordEvent(task, "RECOVERY_REQUIRED", previous, task.getStatus(), null);
    }

    private void recordEvent(Task task, String eventType, TaskStatus from, TaskStatus to, User actor) {
        taskEventRepository.save(new TaskEvent(task, eventType, from, to, actor));
        outboxEventRepository.save(new OutboxEvent("TASK", task.getId(), "task." + eventType.toLowerCase(), null));
    }
}

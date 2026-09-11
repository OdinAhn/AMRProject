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
 * 로봇 배정(assignRobot)은 실제 자동 배정 알고리즘(7단계)이 없어 "지정 배정"만
 * 제공하며, 아직 REST API로 노출하지 않는다 — 통합 테스트에서만 사용한다.
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
    private final RobotGateway robotGateway;

    public TaskService(
            TaskRepository taskRepository,
            TaskStepRepository taskStepRepository,
            TaskAssignmentRepository taskAssignmentRepository,
            TaskEventRepository taskEventRepository,
            OutboxEventRepository outboxEventRepository,
            RobotCommandRepository robotCommandRepository,
            StationService stationService,
            RobotGateway robotGateway
    ) {
        this.taskRepository = taskRepository;
        this.taskStepRepository = taskStepRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.taskEventRepository = taskEventRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.robotCommandRepository = robotCommandRepository;
        this.stationService = stationService;
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

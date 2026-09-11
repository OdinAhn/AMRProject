package com.amrproject.service;

import com.amrproject.domain.entity.Robot;
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
import com.amrproject.repository.TaskAssignmentRepository;
import com.amrproject.repository.TaskEventRepository;
import com.amrproject.repository.TaskRepository;
import com.amrproject.repository.TaskStepRepository;
import com.amrproject.domain.entity.OutboxEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 작업 생성·조회·취소와 상태 전이 기록(TaskEvent)·Outbox 기록을 담당한다.
 * 로봇 배정(assignRobot)은 실제 자동 배정 알고리즘(7단계)이 없어 "지정 배정"만
 * 제공하며, 아직 REST API로 노출하지 않는다 — 통합 테스트에서만 사용한다.
 */
@Service
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskStepRepository taskStepRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final TaskEventRepository taskEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final StationService stationService;

    public TaskService(
            TaskRepository taskRepository,
            TaskStepRepository taskStepRepository,
            TaskAssignmentRepository taskAssignmentRepository,
            TaskEventRepository taskEventRepository,
            OutboxEventRepository outboxEventRepository,
            StationService stationService
    ) {
        this.taskRepository = taskRepository;
        this.taskStepRepository = taskStepRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.taskEventRepository = taskEventRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.stationService = stationService;
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
        taskStepRepository.findByTaskIdAndStepNo(taskId, 1).ifPresent(TaskStep::start);
        recordEvent(task, "STARTED", previous, task.getStatus(), null);
        return task;
    }

    /**
     * 현재 RUNNING인 단계를 완료 처리하고 다음 단계를 시작한다.
     * 마지막 단계면 작업을 SUCCEEDED로 전이한다.
     * 실제로는 로봇 명령 완료(4단계)나 작업자 버튼(5단계)이 이 메서드를 호출하게 된다.
     */
    @Transactional
    public Task completeCurrentStep(Long taskId) {
        Task task = getById(taskId);
        List<TaskStep> steps = taskStepRepository.findByTaskIdOrderByStepNoAsc(taskId);
        TaskStep current = steps.stream()
                .filter(step -> step.getStatus() == TaskStepStatus.RUNNING)
                .findFirst()
                .orElseThrow(() -> new InvalidStateTransitionException("NO_RUNNING_STEP", "진행 중인 단계가 없습니다."));
        current.succeed();

        Optional<TaskStep> next = steps.stream()
                .filter(step -> step.getStepNo().equals(current.getStepNo() + 1))
                .findFirst();

        if (next.isPresent()) {
            next.get().start();
        } else {
            TaskStatus previous = task.getStatus();
            task.succeed();
            recordEvent(task, "SUCCEEDED", previous, task.getStatus(), null);
        }
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

    private void recordEvent(Task task, String eventType, TaskStatus from, TaskStatus to, User actor) {
        taskEventRepository.save(new TaskEvent(task, eventType, from, to, actor));
        outboxEventRepository.save(new OutboxEvent("TASK", task.getId(), "task." + eventType.toLowerCase(), null));
    }
}

package com.amrproject.domain.entity;

import com.amrproject.exception.InvalidStateTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 참고: docs/STATE_MACHINES.md §3. 실제 게이트웨이/시뮬레이터 연동은 4단계에서 이뤄진다 —
 * 이 클래스는 스키마와 상태 전이 규칙만 준비한다.
 */
@Entity
@Table(name = "robot_commands")
public class RobotCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "command_no", nullable = false, unique = true, length = 50)
    private String commandNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_step_id")
    private TaskStep taskStep;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "robot_id", nullable = false)
    private Robot robot;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false, length = 20)
    private RobotCommandType commandType;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RobotCommandStatus status;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected RobotCommand() {
    }

    public RobotCommand(String commandNo, TaskStep taskStep, Robot robot, RobotCommandType commandType, String payloadJson) {
        this.commandNo = commandNo;
        this.taskStep = taskStep;
        this.robot = robot;
        this.commandType = commandType;
        this.payloadJson = payloadJson;
        this.status = RobotCommandStatus.PENDING;
    }

    public void send() {
        requireStatus(RobotCommandStatus.PENDING, "SEND");
        this.status = RobotCommandStatus.SENT;
        this.sentAt = Instant.now();
    }

    public void accept() {
        requireStatus(RobotCommandStatus.SENT, "ACCEPT");
        this.status = RobotCommandStatus.ACCEPTED;
        this.acceptedAt = Instant.now();
    }

    public void startRunning() {
        requireStatus(RobotCommandStatus.ACCEPTED, "START_RUNNING");
        this.status = RobotCommandStatus.RUNNING;
    }

    public void succeed() {
        requireOneOf("SUCCEED", RobotCommandStatus.RUNNING, RobotCommandStatus.UNKNOWN);
        this.status = RobotCommandStatus.SUCCEEDED;
        this.completedAt = Instant.now();
    }

    public void fail() {
        requireOneOf("FAIL", RobotCommandStatus.SENT, RobotCommandStatus.RUNNING, RobotCommandStatus.UNKNOWN);
        this.status = RobotCommandStatus.FAILED;
        this.completedAt = Instant.now();
    }

    /**
     * 응답 타임아웃/통신 단절 등 실행 여부를 알 수 없는 경우.
     * docs/STATE_MACHINES.md §3 — 이 상태를 임의로 FAILED로 간주해 재전송하지 않는다.
     */
    public void markUnknown() {
        requireOneOf("MARK_UNKNOWN", RobotCommandStatus.SENT, RobotCommandStatus.RUNNING);
        this.status = RobotCommandStatus.UNKNOWN;
    }

    public void resumeRunning() {
        requireStatus(RobotCommandStatus.UNKNOWN, "RESUME_RUNNING");
        this.status = RobotCommandStatus.RUNNING;
    }

    private void requireStatus(RobotCommandStatus required, String action) {
        if (status != required) {
            throw new InvalidStateTransitionException(
                    "ROBOT_COMMAND_" + action + "_NOT_ALLOWED",
                    "명령 상태가 " + required + "가 아니어서(현재: " + status + ") " + action + "을(를) 수행할 수 없습니다."
            );
        }
    }

    private void requireOneOf(String action, RobotCommandStatus... allowed) {
        for (RobotCommandStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new InvalidStateTransitionException(
                "ROBOT_COMMAND_" + action + "_NOT_ALLOWED",
                "명령 상태(" + status + ")에서는 " + action + "을(를) 수행할 수 없습니다."
        );
    }

    public Long getId() {
        return id;
    }

    public String getCommandNo() {
        return commandNo;
    }

    public TaskStep getTaskStep() {
        return taskStep;
    }

    public Robot getRobot() {
        return robot;
    }

    public RobotCommandType getCommandType() {
        return commandType;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public RobotCommandStatus getStatus() {
        return status;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}

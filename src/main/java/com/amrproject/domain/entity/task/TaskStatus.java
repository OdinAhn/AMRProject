package com.amrproject.domain.entity.task;

public enum TaskStatus {
    QUEUED,
    ASSIGNED,
    RUNNING,
    PAUSED,
    CANCEL_REQUESTED,
    CANCELED,
    RECOVERY_REQUIRED,
    SUCCEEDED,
    FAILED
}

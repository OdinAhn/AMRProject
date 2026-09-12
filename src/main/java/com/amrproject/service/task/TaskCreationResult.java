package com.amrproject.service.task;

import com.amrproject.domain.entity.task.Task;

public record TaskCreationResult(Task task, boolean created) {
}

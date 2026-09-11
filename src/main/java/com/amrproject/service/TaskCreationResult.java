package com.amrproject.service;

import com.amrproject.domain.entity.Task;

public record TaskCreationResult(Task task, boolean created) {
}

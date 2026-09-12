package com.amrproject.dto.response.task;

import java.util.List;

public record DispatchResultResponse(
        List<Long> assignedTaskIds,
        List<Long> chargeTaskIds
) {
}

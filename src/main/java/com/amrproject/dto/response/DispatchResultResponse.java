package com.amrproject.dto.response;

import java.util.List;

public record DispatchResultResponse(
        List<Long> assignedTaskIds,
        List<Long> chargeTaskIds
) {
}

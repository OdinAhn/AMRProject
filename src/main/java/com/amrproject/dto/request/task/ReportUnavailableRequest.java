package com.amrproject.dto.request.task;

import jakarta.validation.constraints.NotBlank;

public record ReportUnavailableRequest(
        @NotBlank String reason
) {
}

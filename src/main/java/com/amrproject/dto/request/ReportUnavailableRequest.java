package com.amrproject.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ReportUnavailableRequest(
        @NotBlank String reason
) {
}

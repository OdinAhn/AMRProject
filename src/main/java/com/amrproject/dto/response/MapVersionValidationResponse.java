package com.amrproject.dto.response;

import java.util.List;

public record MapVersionValidationResponse(
        boolean valid,
        List<String> errors,
        MapVersionResponse mapVersion
) {
}

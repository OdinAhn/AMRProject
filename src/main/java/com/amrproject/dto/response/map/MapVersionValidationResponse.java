package com.amrproject.dto.response.map;

import java.util.List;

public record MapVersionValidationResponse(
        boolean valid,
        List<String> errors,
        MapVersionResponse mapVersion
) {
}

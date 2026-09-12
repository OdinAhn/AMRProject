package com.amrproject.dto.request;

public record CreateMapVersionRequest(
        String filePath,
        String coordinateSystem
) {
}

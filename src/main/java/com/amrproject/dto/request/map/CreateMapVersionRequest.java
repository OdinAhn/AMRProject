package com.amrproject.dto.request.map;

public record CreateMapVersionRequest(
        String filePath,
        String coordinateSystem
) {
}

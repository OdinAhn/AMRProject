package com.amrproject.dto.response.map;

import com.amrproject.domain.entity.map.MapEntity;

public record MapResponse(
        Long id,
        Long siteId,
        String name
) {

    public static MapResponse from(MapEntity map) {
        return new MapResponse(map.getId(), map.getSite().getId(), map.getName());
    }
}

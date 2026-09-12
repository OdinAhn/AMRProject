package com.amrproject.dto.response.site;

import com.amrproject.domain.entity.site.StationMapBinding;

public record StationMapBindingResponse(
        Long id,
        Long stationId,
        Long mapVersionId,
        Long nodeId
) {

    public static StationMapBindingResponse from(StationMapBinding binding) {
        return new StationMapBindingResponse(
                binding.getId(),
                binding.getStation().getId(),
                binding.getMapVersion().getId(),
                binding.getNode().getId()
        );
    }
}

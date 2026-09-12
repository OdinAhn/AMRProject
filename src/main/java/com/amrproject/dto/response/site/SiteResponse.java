package com.amrproject.dto.response.site;

import com.amrproject.domain.entity.site.Site;
import com.amrproject.domain.entity.site.SiteStatus;

import java.time.Instant;

public record SiteResponse(
        Long id,
        String code,
        String name,
        SiteStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static SiteResponse from(Site site) {
        return new SiteResponse(
                site.getId(),
                site.getCode(),
                site.getName(),
                site.getStatus(),
                site.getCreatedAt(),
                site.getUpdatedAt()
        );
    }
}

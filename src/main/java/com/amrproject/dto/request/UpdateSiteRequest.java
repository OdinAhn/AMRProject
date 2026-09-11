package com.amrproject.dto.request;

import com.amrproject.domain.entity.SiteStatus;

public record UpdateSiteRequest(
        String name,
        SiteStatus status
) {
}

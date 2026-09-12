package com.amrproject.dto.request.site;

import com.amrproject.domain.entity.site.SiteStatus;

public record UpdateSiteRequest(
        String name,
        SiteStatus status
) {
}

package com.amrproject.repository.site;

import com.amrproject.domain.entity.site.Site;
import com.amrproject.domain.entity.site.SiteStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SiteRepository extends JpaRepository<Site, Long> {

    boolean existsByCode(String code);

    List<Site> findByStatus(SiteStatus status);
}

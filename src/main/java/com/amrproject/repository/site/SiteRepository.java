package com.amrproject.repository.site;

import com.amrproject.domain.entity.site.Site;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteRepository extends JpaRepository<Site, Long> {

    boolean existsByCode(String code);
}

package com.amrproject.repository;

import com.amrproject.domain.entity.Site;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteRepository extends JpaRepository<Site, Long> {

    boolean existsByCode(String code);
}

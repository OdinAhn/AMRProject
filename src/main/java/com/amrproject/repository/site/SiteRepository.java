package com.amrproject.repository.site;

import com.amrproject.domain.entity.site.Site;
import com.amrproject.domain.entity.site.SiteStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SiteRepository extends JpaRepository<Site, Long> {

    boolean existsByCode(String code);

    List<Site> findByStatus(SiteStatus status);

    /** 46단계: 교통 관리 경로 계획을 물류센터 단위로 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Site s where s.id = :id")
    Optional<Site> findByIdForUpdate(Long id);
}

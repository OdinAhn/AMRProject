package com.amrproject.repository.site;

import com.amrproject.domain.entity.site.Station;
import com.amrproject.domain.entity.site.StationType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StationRepository extends JpaRepository<Station, Long> {

    List<Station> findBySiteId(Long siteId);

    boolean existsBySiteIdAndCode(Long siteId, String code);

    List<Station> findBySiteIdAndStationType(Long siteId, StationType stationType);

    /** 31단계: 작업장 수용량 검사(예약 수 세기 → 예약 저장)를 작업장 단위로 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Station s where s.id = :id")
    Optional<Station> findByIdForUpdate(Long id);
}

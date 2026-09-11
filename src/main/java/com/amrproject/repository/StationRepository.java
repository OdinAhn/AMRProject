package com.amrproject.repository;

import com.amrproject.domain.entity.Station;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StationRepository extends JpaRepository<Station, Long> {

    List<Station> findBySiteId(Long siteId);

    boolean existsBySiteIdAndCode(Long siteId, String code);
}

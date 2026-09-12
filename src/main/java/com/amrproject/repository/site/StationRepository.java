package com.amrproject.repository.site;

import com.amrproject.domain.entity.site.Station;
import com.amrproject.domain.entity.site.StationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StationRepository extends JpaRepository<Station, Long> {

    List<Station> findBySiteId(Long siteId);

    boolean existsBySiteIdAndCode(Long siteId, String code);

    List<Station> findBySiteIdAndStationType(Long siteId, StationType stationType);
}

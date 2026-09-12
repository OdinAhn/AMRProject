package com.amrproject.repository.map;

import com.amrproject.domain.entity.map.Zone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZoneRepository extends JpaRepository<Zone, Long> {

    List<Zone> findByMapVersionId(Long mapVersionId);

    boolean existsByMapVersionIdAndCode(Long mapVersionId, String code);

    int countByMapVersionId(Long mapVersionId);
}

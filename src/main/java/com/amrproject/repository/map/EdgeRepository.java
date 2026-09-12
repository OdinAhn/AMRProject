package com.amrproject.repository.map;

import com.amrproject.domain.entity.map.Edge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EdgeRepository extends JpaRepository<Edge, Long> {

    List<Edge> findByMapVersionId(Long mapVersionId);

    int countByMapVersionId(Long mapVersionId);
}

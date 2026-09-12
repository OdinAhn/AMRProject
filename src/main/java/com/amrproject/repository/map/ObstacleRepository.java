package com.amrproject.repository.map;

import com.amrproject.domain.entity.map.Obstacle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ObstacleRepository extends JpaRepository<Obstacle, Long> {

    List<Obstacle> findByMapVersionId(Long mapVersionId);
}

package com.amrproject.repository;

import com.amrproject.domain.entity.Node;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NodeRepository extends JpaRepository<Node, Long> {

    List<Node> findByMapVersionId(Long mapVersionId);

    boolean existsByMapVersionIdAndCode(Long mapVersionId, String code);

    int countByMapVersionId(Long mapVersionId);
}

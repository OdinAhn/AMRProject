package com.amrproject.repository;

import com.amrproject.domain.entity.CommandEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommandEventRepository extends JpaRepository<CommandEvent, Long> {
}

package com.amrproject.service;

import com.amrproject.domain.entity.Robot;
import com.amrproject.domain.entity.Site;
import com.amrproject.dto.request.CreateRobotRequest;
import com.amrproject.dto.request.UpdateRobotLifecycleStatusRequest;
import com.amrproject.exception.DuplicateResourceException;
import com.amrproject.exception.EntityNotFoundException;
import com.amrproject.repository.RobotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class RobotService {

    private final RobotRepository robotRepository;
    private final SiteService siteService;

    public RobotService(RobotRepository robotRepository, SiteService siteService) {
        this.robotRepository = robotRepository;
        this.siteService = siteService;
    }

    @Transactional
    public Robot create(CreateRobotRequest request) {
        Site site = siteService.getById(request.siteId());
        if (robotRepository.existsByCode(request.code())) {
            throw new DuplicateResourceException("ROBOT_CODE_DUPLICATE", "이미 존재하는 robot code입니다: " + request.code());
        }
        return robotRepository.save(new Robot(request.code(), request.name(), request.model(), request.manufacturer(), site));
    }

    public Robot getById(Long id) {
        return robotRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ROBOT_NOT_FOUND", "로봇을 찾을 수 없습니다: " + id));
    }

    public List<Robot> getAll(Long siteId) {
        if (siteId != null) {
            siteService.getById(siteId);
            return robotRepository.findBySiteId(siteId);
        }
        return robotRepository.findAll();
    }

    @Transactional
    public Robot updateLifecycleStatus(Long id, UpdateRobotLifecycleStatusRequest request) {
        Robot robot = getById(id);
        robot.changeLifecycleStatus(request.lifecycleStatus());
        return robot;
    }
}

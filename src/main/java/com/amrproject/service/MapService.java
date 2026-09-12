package com.amrproject.service;

import com.amrproject.domain.entity.MapEntity;
import com.amrproject.domain.entity.Site;
import com.amrproject.dto.request.CreateMapRequest;
import com.amrproject.exception.EntityNotFoundException;
import com.amrproject.repository.MapRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class MapService {

    private final MapRepository mapRepository;
    private final SiteService siteService;

    public MapService(MapRepository mapRepository, SiteService siteService) {
        this.mapRepository = mapRepository;
        this.siteService = siteService;
    }

    @Transactional
    public MapEntity create(Long siteId, CreateMapRequest request) {
        Site site = siteService.getById(siteId);
        return mapRepository.save(new MapEntity(site, request.name()));
    }

    public MapEntity getById(Long id) {
        return mapRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("MAP_NOT_FOUND", "지도를 찾을 수 없습니다: " + id));
    }

    public List<MapEntity> getBySite(Long siteId) {
        siteService.getById(siteId);
        return mapRepository.findBySiteId(siteId);
    }
}

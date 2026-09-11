package com.amrproject.service;

import com.amrproject.domain.entity.Site;
import com.amrproject.domain.entity.Station;
import com.amrproject.dto.request.CreateStationRequest;
import com.amrproject.dto.request.UpdateStationRequest;
import com.amrproject.exception.DuplicateResourceException;
import com.amrproject.exception.EntityNotFoundException;
import com.amrproject.repository.StationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class StationService {

    private final StationRepository stationRepository;
    private final SiteService siteService;

    public StationService(StationRepository stationRepository, SiteService siteService) {
        this.stationRepository = stationRepository;
        this.siteService = siteService;
    }

    @Transactional
    public Station create(Long siteId, CreateStationRequest request) {
        Site site = siteService.getById(siteId);
        if (stationRepository.existsBySiteIdAndCode(siteId, request.code())) {
            throw new DuplicateResourceException(
                    "STATION_CODE_DUPLICATE",
                    "이미 존재하는 station code입니다: " + request.code()
            );
        }
        return stationRepository.save(new Station(site, request.code(), request.name(), request.stationType()));
    }

    public Station getById(Long id) {
        return stationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("STATION_NOT_FOUND", "작업장을 찾을 수 없습니다: " + id));
    }

    public List<Station> getBySite(Long siteId) {
        siteService.getById(siteId);
        return stationRepository.findBySiteId(siteId);
    }

    @Transactional
    public Station update(Long id, UpdateStationRequest request) {
        Station station = getById(id);
        if (request.name() != null) {
            station.rename(request.name());
        }
        if (request.status() != null) {
            station.changeStatus(request.status());
        }
        return station;
    }
}

package com.amrproject.service;

import com.amrproject.domain.entity.site.Site;
import com.amrproject.dto.request.site.CreateSiteRequest;
import com.amrproject.dto.request.site.UpdateSiteRequest;
import com.amrproject.exception.DuplicateResourceException;
import com.amrproject.exception.EntityNotFoundException;
import com.amrproject.repository.site.SiteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SiteService {

    private final SiteRepository siteRepository;

    public SiteService(SiteRepository siteRepository) {
        this.siteRepository = siteRepository;
    }

    @Transactional
    public Site create(CreateSiteRequest request) {
        if (siteRepository.existsByCode(request.code())) {
            throw new DuplicateResourceException("SITE_CODE_DUPLICATE", "이미 존재하는 site code입니다: " + request.code());
        }
        return siteRepository.save(new Site(request.code(), request.name()));
    }

    public Site getById(Long id) {
        return siteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("SITE_NOT_FOUND", "물류센터를 찾을 수 없습니다: " + id));
    }

    public List<Site> getAll() {
        return siteRepository.findAll();
    }

    @Transactional
    public Site update(Long id, UpdateSiteRequest request) {
        Site site = getById(id);
        if (request.name() != null) {
            site.rename(request.name());
        }
        if (request.status() != null) {
            site.changeStatus(request.status());
        }
        return site;
    }
}

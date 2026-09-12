package com.amrproject.domain.entity.site;

import com.amrproject.domain.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sites")
public class Site extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SiteStatus status;

    protected Site() {
    }

    public Site(String code, String name) {
        this.code = code;
        this.name = name;
        this.status = SiteStatus.ACTIVE;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeStatus(SiteStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public SiteStatus getStatus() {
        return status;
    }
}

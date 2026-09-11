package com.amrproject.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "robots")
public class Robot extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String model;

    @Column(length = 100)
    private String manufacturer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private RobotLifecycleStatus lifecycleStatus;

    protected Robot() {
    }

    public Robot(String code, String name, String model, String manufacturer, Site site) {
        this.code = code;
        this.name = name;
        this.model = model;
        this.manufacturer = manufacturer;
        this.site = site;
        this.lifecycleStatus = RobotLifecycleStatus.REGISTERED;
    }

    public void changeLifecycleStatus(RobotLifecycleStatus status) {
        this.lifecycleStatus = status;
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

    public String getModel() {
        return model;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public Site getSite() {
        return site;
    }

    public RobotLifecycleStatus getLifecycleStatus() {
        return lifecycleStatus;
    }
}

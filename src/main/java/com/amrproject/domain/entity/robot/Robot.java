package com.amrproject.domain.entity.robot;

import com.amrproject.domain.entity.BaseAuditableEntity;
import com.amrproject.domain.entity.site.Site;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "desired_config_id")
    private RobotConfiguration desiredConfiguration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applied_config_id")
    private RobotConfiguration appliedConfiguration;

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

    public void updateBasicInfo(String name, String model, String manufacturer) {
        this.name = name;
        this.model = model;
        this.manufacturer = manufacturer;
    }

    public void assignDesiredConfiguration(RobotConfiguration configuration) {
        this.desiredConfiguration = configuration;
    }

    public void markConfigurationApplied(RobotConfiguration configuration) {
        this.appliedConfiguration = configuration;
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

    public RobotConfiguration getDesiredConfiguration() {
        return desiredConfiguration;
    }

    public RobotConfiguration getAppliedConfiguration() {
        return appliedConfiguration;
    }
}

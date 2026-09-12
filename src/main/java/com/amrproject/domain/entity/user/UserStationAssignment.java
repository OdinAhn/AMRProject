package com.amrproject.domain.entity.user;

import com.amrproject.domain.entity.site.Station;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "user_station_assignments", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "station_id"}))
@EntityListeners(AuditingEntityListener.class)
public class UserStationAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserStationAssignment() {
    }

    public UserStationAssignment(User user, Station station) {
        this.user = user;
        this.station = station;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Station getStation() {
        return station;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

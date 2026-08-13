package com.roottrace.slo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sli_measurements")
public class SliMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slo_id", nullable = false)
    private Slo slo;

    @Column(name = "measurement_time", nullable = false)
    private Instant measurementTime;

    @Column(name = "total_events", nullable = false)
    private Long totalEvents;

    @Column(name = "good_events", nullable = false)
    private Long goodEvents;

    @Column(name = "bad_events", nullable = false)
    private Long badEvents;

    @Column(nullable = false, precision = 12, scale = 6)
    private BigDecimal value;

    @Column(length = 100)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public SliMeasurement() {
        // JPA
    }

    public SliMeasurement(
            Slo slo,
            Instant measurementTime,
            Long totalEvents,
            Long goodEvents,
            Long badEvents,
            BigDecimal value,
            String source) {
        this.slo = slo;
        this.measurementTime = measurementTime != null ? measurementTime : Instant.now();
        this.totalEvents = totalEvents;
        this.goodEvents = goodEvents;
        this.badEvents = badEvents;
        this.value = value;
        this.source = source;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.measurementTime == null) {
            this.measurementTime = Instant.now();
        }
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Slo getSlo() {
        return slo;
    }

    public void setSlo(Slo slo) {
        this.slo = slo;
    }

    public Instant getMeasurementTime() {
        return measurementTime;
    }

    public void setMeasurementTime(Instant measurementTime) {
        this.measurementTime = measurementTime;
    }

    public Long getTotalEvents() {
        return totalEvents;
    }

    public void setTotalEvents(Long totalEvents) {
        this.totalEvents = totalEvents;
    }

    public Long getGoodEvents() {
        return goodEvents;
    }

    public void setGoodEvents(Long goodEvents) {
        this.goodEvents = goodEvents;
    }

    public Long getBadEvents() {
        return badEvents;
    }

    public void setBadEvents(Long badEvents) {
        this.badEvents = badEvents;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

package com.roottrace.slo;

import com.roottrace.user.User;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "service_slos", uniqueConstraints = {
        @UniqueConstraint(name = "uq_service_slo_name", columnNames = {"service_name", "name"})
})
public class Slo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "service_name", nullable = false, length = 255)
    private String serviceName;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "slo_type", nullable = false, length = 50)
    private SloType sloType;

    @Column(name = "target_percentage", nullable = false, precision = 6, scale = 3)
    private BigDecimal targetPercentage;

    @Column(name = "window_days", nullable = false)
    private Integer windowDays = 30;

    @Column(name = "warning_threshold_percentage", nullable = false, precision = 6, scale = 3)
    private BigDecimal warningThresholdPercentage = BigDecimal.valueOf(99.0);

    @Column(name = "critical_threshold_percentage", nullable = false, precision = 6, scale = 3)
    private BigDecimal criticalThresholdPercentage = BigDecimal.valueOf(95.0);

    @Column(nullable = false)
    private Boolean enabled = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @OneToMany(mappedBy = "slo", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SliMeasurement> measurements = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Slo() {
        // JPA
    }

    public Slo(
            String serviceName,
            String name,
            String description,
            SloType sloType,
            BigDecimal targetPercentage,
            Integer windowDays,
            BigDecimal warningThresholdPercentage,
            BigDecimal criticalThresholdPercentage,
            User createdBy) {
        this.serviceName = serviceName;
        this.name = name;
        this.description = description;
        this.sloType = sloType;
        this.targetPercentage = targetPercentage;
        this.windowDays = windowDays != null ? windowDays : 30;
        this.warningThresholdPercentage = warningThresholdPercentage != null ? warningThresholdPercentage : BigDecimal.valueOf(99.0);
        this.criticalThresholdPercentage = criticalThresholdPercentage != null ? criticalThresholdPercentage : BigDecimal.valueOf(95.0);
        this.enabled = true;
        this.createdBy = createdBy;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.enabled == null) {
            this.enabled = true;
        }
        if (this.windowDays == null) {
            this.windowDays = 30;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public SloType getSloType() {
        return sloType;
    }

    public void setSloType(SloType sloType) {
        this.sloType = sloType;
    }

    public BigDecimal getTargetPercentage() {
        return targetPercentage;
    }

    public void setTargetPercentage(BigDecimal targetPercentage) {
        this.targetPercentage = targetPercentage;
    }

    public Integer getWindowDays() {
        return windowDays;
    }

    public void setWindowDays(Integer windowDays) {
        this.windowDays = windowDays;
    }

    public BigDecimal getWarningThresholdPercentage() {
        return warningThresholdPercentage;
    }

    public void setWarningThresholdPercentage(BigDecimal warningThresholdPercentage) {
        this.warningThresholdPercentage = warningThresholdPercentage;
    }

    public BigDecimal getCriticalThresholdPercentage() {
        return criticalThresholdPercentage;
    }

    public void setCriticalThresholdPercentage(BigDecimal criticalThresholdPercentage) {
        this.criticalThresholdPercentage = criticalThresholdPercentage;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public List<SliMeasurement> getMeasurements() {
        return measurements;
    }

    public void setMeasurements(List<SliMeasurement> measurements) {
        this.measurements = measurements;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

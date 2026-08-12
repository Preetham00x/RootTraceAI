package com.roottrace.ai.diagnosis;

import com.roottrace.incident.Incident;
import com.roottrace.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ai_diagnoses")
public class AiDiagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "probable_root_cause", nullable = false, columnDefinition = "TEXT")
    private String probableRootCause;

    @Column(nullable = false)
    private Double confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contributing_factors", nullable = false, columnDefinition = "jsonb")
    private List<String> contributingFactors = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommended_actions", nullable = false, columnDefinition = "jsonb")
    private List<String> recommendedActions = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "diagnosis", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AiDiagnosisEvidence> evidence = new ArrayList<>();

    @OneToMany(mappedBy = "diagnosis", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AiDiagnosisCitation> citations = new ArrayList<>();

    protected AiDiagnosis() {
    }

    public AiDiagnosis(Incident incident, String summary, String probableRootCause,
                       Double confidence, List<String> contributingFactors,
                       List<String> recommendedActions, User createdBy) {
        this.incident = incident;
        this.summary = summary;
        this.probableRootCause = probableRootCause;
        this.confidence = confidence;
        this.contributingFactors = contributingFactors != null ? contributingFactors : new ArrayList<>();
        this.recommendedActions = recommendedActions != null ? recommendedActions : new ArrayList<>();
        this.createdBy = createdBy;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public void addEvidence(AiDiagnosisEvidence ev) {
        evidence.add(ev);
        ev.setDiagnosis(this);
    }

    public void addCitation(AiDiagnosisCitation cit) {
        citations.add(cit);
        cit.setDiagnosis(this);
    }

    // Getters
    public UUID getId() { return id; }
    public Incident getIncident() { return incident; }
    public String getSummary() { return summary; }
    public String getProbableRootCause() { return probableRootCause; }
    public Double getConfidence() { return confidence; }
    public List<String> getContributingFactors() { return contributingFactors; }
    public List<String> getRecommendedActions() { return recommendedActions; }
    public User getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public List<AiDiagnosisEvidence> getEvidence() { return evidence; }
    public List<AiDiagnosisCitation> getCitations() { return citations; }
}

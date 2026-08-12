package com.roottrace.ai.diagnosis;

import com.roottrace.knowledge.KnowledgeChunk;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_diagnosis_evidence")
public class AiDiagnosisEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagnosis_id", nullable = false)
    private AiDiagnosis diagnosis;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chunk_id", nullable = false)
    private KnowledgeChunk chunk;

    @Column(name = "relevance_score")
    private Double relevanceScore;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiDiagnosisEvidence() {
    }

    public AiDiagnosisEvidence(KnowledgeChunk chunk, Double relevanceScore, String reason) {
        this.chunk = chunk;
        this.relevanceScore = relevanceScore;
        this.reason = reason;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public AiDiagnosis getDiagnosis() { return diagnosis; }
    public void setDiagnosis(AiDiagnosis diagnosis) { this.diagnosis = diagnosis; }
    public KnowledgeChunk getChunk() { return chunk; }
    public Double getRelevanceScore() { return relevanceScore; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}

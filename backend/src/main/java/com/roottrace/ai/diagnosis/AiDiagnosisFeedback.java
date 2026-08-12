package com.roottrace.ai.diagnosis;

import com.roottrace.user.User;
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
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_diagnosis_feedback", uniqueConstraints = {
        @UniqueConstraint(name = "uq_feedback_diagnosis_user", columnNames = {"diagnosis_id", "user_id"})
})
public class AiDiagnosisFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagnosis_id", nullable = false)
    private AiDiagnosis diagnosis;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private boolean helpful;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiDiagnosisFeedback() {
    }

    public AiDiagnosisFeedback(AiDiagnosis diagnosis, User user, boolean helpful, String comment) {
        this.diagnosis = diagnosis;
        this.user = user;
        this.helpful = helpful;
        this.comment = comment;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public AiDiagnosis getDiagnosis() { return diagnosis; }
    public User getUser() { return user; }
    public boolean isHelpful() { return helpful; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }
}

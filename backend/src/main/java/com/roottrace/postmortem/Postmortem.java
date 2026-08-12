package com.roottrace.postmortem;

import com.roottrace.incident.Incident;
import com.roottrace.postmortem.dto.PostmortemTimelineEntry;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "postmortems")
public class Postmortem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false, unique = true)
    private Incident incident;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "impact_summary", nullable = false, columnDefinition = "TEXT")
    private String impactSummary;

    @Column(name = "root_cause_analysis", nullable = false, columnDefinition = "TEXT")
    private String rootCauseAnalysis;

    @Column(name = "resolution_summary", nullable = false, columnDefinition = "TEXT")
    private String resolutionSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "timeline", nullable = false, columnDefinition = "jsonb")
    private List<PostmortemTimelineEntry> timeline = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "lessons_learned", nullable = false, columnDefinition = "jsonb")
    private List<String> lessonsLearned = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PostmortemStatus status = PostmortemStatus.DRAFT;

    @Column(name = "downtime_minutes")
    private Long downtimeMinutes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false, updatable = false)
    private User createdBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "postmortem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostmortemActionItem> actionItems = new ArrayList<>();

    protected Postmortem() {
        // JPA
    }

    public Postmortem(
            Incident incident,
            String title,
            String summary,
            String impactSummary,
            String rootCauseAnalysis,
            String resolutionSummary,
            List<PostmortemTimelineEntry> timeline,
            List<String> lessonsLearned,
            Long downtimeMinutes,
            User createdBy) {
        this.incident = incident;
        this.title = title;
        this.summary = summary;
        this.impactSummary = impactSummary;
        this.rootCauseAnalysis = rootCauseAnalysis;
        this.resolutionSummary = resolutionSummary;
        this.timeline = timeline != null ? new ArrayList<>(timeline) : new ArrayList<>();
        this.lessonsLearned = lessonsLearned != null ? new ArrayList<>(lessonsLearned) : new ArrayList<>();
        this.downtimeMinutes = downtimeMinutes;
        this.createdBy = createdBy;
        this.status = PostmortemStatus.DRAFT;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = PostmortemStatus.DRAFT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void addActionItem(PostmortemActionItem actionItem) {
        actionItems.add(actionItem);
        actionItem.setPostmortem(this);
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Incident getIncident() {
        return incident;
    }

    public void setIncident(Incident incident) {
        this.incident = incident;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getImpactSummary() {
        return impactSummary;
    }

    public void setImpactSummary(String impactSummary) {
        this.impactSummary = impactSummary;
    }

    public String getRootCauseAnalysis() {
        return rootCauseAnalysis;
    }

    public void setRootCauseAnalysis(String rootCauseAnalysis) {
        this.rootCauseAnalysis = rootCauseAnalysis;
    }

    public String getResolutionSummary() {
        return resolutionSummary;
    }

    public void setResolutionSummary(String resolutionSummary) {
        this.resolutionSummary = resolutionSummary;
    }

    public List<PostmortemTimelineEntry> getTimeline() {
        return timeline;
    }

    public void setTimeline(List<PostmortemTimelineEntry> timeline) {
        this.timeline = timeline != null ? new ArrayList<>(timeline) : new ArrayList<>();
    }

    public List<String> getLessonsLearned() {
        return lessonsLearned;
    }

    public void setLessonsLearned(List<String> lessonsLearned) {
        this.lessonsLearned = lessonsLearned != null ? new ArrayList<>(lessonsLearned) : new ArrayList<>();
    }

    public PostmortemStatus getStatus() {
        return status;
    }

    public void setStatus(PostmortemStatus status) {
        this.status = status;
    }

    public Long getDowntimeMinutes() {
        return downtimeMinutes;
    }

    public void setDowntimeMinutes(Long downtimeMinutes) {
        this.downtimeMinutes = downtimeMinutes;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<PostmortemActionItem> getActionItems() {
        return actionItems;
    }

    public void setActionItems(List<PostmortemActionItem> actionItems) {
        this.actionItems = actionItems;
    }
}

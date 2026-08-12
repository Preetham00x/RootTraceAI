package com.roottrace.postmortem;

import com.roottrace.user.User;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "postmortem_action_items")
public class PostmortemActionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "postmortem_id", nullable = false)
    private Postmortem postmortem;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ActionItemCategory category = ActionItemCategory.PREVENT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ActionItemPriority priority = ActionItemPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ActionItemStatus status = ActionItemStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private User assignedTo;

    @Column(name = "due_date")
    private Instant dueDate;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PostmortemActionItem() {
        // JPA
    }

    public PostmortemActionItem(
            Postmortem postmortem,
            String title,
            String description,
            ActionItemCategory category,
            ActionItemPriority priority,
            User assignedTo,
            Instant dueDate) {
        this.postmortem = postmortem;
        this.title = title;
        this.description = description;
        this.category = category != null ? category : ActionItemCategory.PREVENT;
        this.priority = priority != null ? priority : ActionItemPriority.MEDIUM;
        this.assignedTo = assignedTo;
        this.dueDate = dueDate;
        this.status = ActionItemStatus.OPEN;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = ActionItemStatus.OPEN;
        }
        if (this.category == null) {
            this.category = ActionItemCategory.PREVENT;
        }
        if (this.priority == null) {
            this.priority = ActionItemPriority.MEDIUM;
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

    public Postmortem getPostmortem() {
        return postmortem;
    }

    public void setPostmortem(Postmortem postmortem) {
        this.postmortem = postmortem;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ActionItemCategory getCategory() {
        return category;
    }

    public void setCategory(ActionItemCategory category) {
        this.category = category;
    }

    public ActionItemPriority getPriority() {
        return priority;
    }

    public void setPriority(ActionItemPriority priority) {
        this.priority = priority;
    }

    public ActionItemStatus getStatus() {
        return status;
    }

    public void setStatus(ActionItemStatus status) {
        this.status = status;
    }

    public User getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(User assignedTo) {
        this.assignedTo = assignedTo;
    }

    public Instant getDueDate() {
        return dueDate;
    }

    public void setDueDate(Instant dueDate) {
        this.dueDate = dueDate;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
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
}

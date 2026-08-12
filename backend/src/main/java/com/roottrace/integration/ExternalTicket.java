package com.roottrace.integration;

import com.roottrace.incident.Incident;
import com.roottrace.postmortem.PostmortemActionItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_tickets", uniqueConstraints = {
        @UniqueConstraint(name = "uq_external_ticket_action", columnNames = {"provider", "action_item_id"})
})
public class ExternalTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_item_id")
    private PostmortemActionItem actionItem;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "external_ticket_id", nullable = false, length = 100)
    private String externalTicketId;

    @Column(name = "external_url", length = 500)
    private String externalUrl;

    @Column(nullable = false, length = 50)
    private String status = "CREATED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ExternalTicket() {
        // JPA
    }

    public ExternalTicket(
            Incident incident,
            PostmortemActionItem actionItem,
            String provider,
            String externalTicketId,
            String externalUrl) {
        this.incident = incident;
        this.actionItem = actionItem;
        this.provider = provider;
        this.externalTicketId = externalTicketId;
        this.externalUrl = externalUrl;
        this.status = "CREATED";
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = "CREATED";
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

    public Incident getIncident() {
        return incident;
    }

    public void setIncident(Incident incident) {
        this.incident = incident;
    }

    public PostmortemActionItem getActionItem() {
        return actionItem;
    }

    public void setActionItem(PostmortemActionItem actionItem) {
        this.actionItem = actionItem;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getExternalTicketId() {
        return externalTicketId;
    }

    public void setExternalTicketId(String externalTicketId) {
        this.externalTicketId = externalTicketId;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public void setExternalUrl(String externalUrl) {
        this.externalUrl = externalUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

package com.roottrace.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.common.audit.AuditEventType;
import com.roottrace.common.audit.AuditService;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.integration.dto.WebhookIngestionResponse;
import com.roottrace.user.Role;
import com.roottrace.user.User;
import com.roottrace.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WebhookEventService {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventService.class);

    private final WebhookEventRepository webhookEventRepository;
    private final IncidentRepository incidentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public WebhookEventService(
            WebhookEventRepository webhookEventRepository,
            IncidentRepository incidentRepository,
            UserRepository userRepository,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.webhookEventRepository = webhookEventRepository;
        this.incidentRepository = incidentRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WebhookIngestionResponse processWebhookAlert(
            String provider,
            ExternalIncidentSource.NormalizedAlert alert,
            Object rawPayload) {

        if (alert == null || alert.externalEventId() == null || alert.externalEventId().isBlank()) {
            throw new BadRequestException("Invalid alert: missing external event identifier");
        }

        String eventId = alert.externalEventId().trim();
        Optional<WebhookEvent> existingOpt = webhookEventRepository.findByProviderAndExternalEventId(provider, eventId);

        if (existingOpt.isPresent()) {
            WebhookEvent existing = existingOpt.get();
            log.info("Duplicate webhook received from provider [{}] with event ID [{}]", provider, eventId);
            UUID linkedIncId = existing.getIncident() != null ? existing.getIncident().getId() : null;
            return new WebhookIngestionResponse(
                    "DUPLICATE",
                    "Webhook event already processed.",
                    linkedIncId,
                    eventId,
                    true
            );
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(rawPayload);
        } catch (Exception ex) {
            payloadJson = String.valueOf(rawPayload);
        }

        WebhookEvent event = new WebhookEvent(provider, eventId, alert.status(), payloadJson);

        // Check if an existing open incident with identical service and matching title exists for deduplication
        String serviceName = (alert.service() != null && !alert.service().isBlank()) ? alert.service() : "unknown-service";
        String title = (alert.title() != null && !alert.title().isBlank()) ? alert.title() : ("Alert: " + provider + " " + eventId);

        List<Incident> openIncidents = incidentRepository.findAllNotDeleted(org.springframework.data.domain.Pageable.unpaged()).getContent()
                .stream()
                .filter(i -> i.getService() != null && i.getService().equalsIgnoreCase(serviceName)
                        && (i.getStatus() == IncidentStatus.OPEN || i.getStatus() == IncidentStatus.INVESTIGATING)
                        && i.getTitle() != null && i.getTitle().equalsIgnoreCase(title))
                .toList();

        Incident targetIncident;
        boolean isNew = false;

        if (!openIncidents.isEmpty()) {
            targetIncident = openIncidents.get(0);
            log.info("Updating existing active incident [{}] with incoming webhook [{}]", targetIncident.getId(), eventId);
            if (alert.severity() != null && alert.severity().ordinal() < targetIncident.getSeverity().ordinal()) {
                // Elevate severity if incoming alert is more severe (CRITICAL is index 0 or higher severity)
                targetIncident.setSeverity(alert.severity());
            }
            targetIncident.setDescription(targetIncident.getDescription() + "\n\n[Updated by " + provider + " at " + Instant.now() + "]: " + alert.description());
            incidentRepository.save(targetIncident);
        } else {
            // Create new incident
            User systemUser = getSystemUser();
            Incident newInc = new Incident();
            newInc.setTitle(title);
            newInc.setDescription(alert.description() != null ? alert.description() : "Automated alert received from " + provider);
            newInc.setService(serviceName);
            newInc.setEnvironment(alert.environment() != null ? alert.environment() : "production");
            newInc.setSeverity(alert.severity() != null ? alert.severity() : IncidentSeverity.MEDIUM);
            newInc.setStatus(IncidentStatus.OPEN);
            newInc.setCreatedBy(systemUser);

            targetIncident = incidentRepository.save(newInc);
            isNew = true;
            log.info("Created new incident [{}] from webhook [{}] (Provider: {})", targetIncident.getId(), eventId, provider);
        }

        event.setIncident(targetIncident);
        event.setProcessingStatus(WebhookProcessingStatus.PROCESSED);
        event.setProcessedAt(Instant.now());
        webhookEventRepository.save(event);

        if (isNew) {
            auditService.record(
                    AuditEventType.EXTERNAL_INCIDENT_CREATED,
                    "Incident",
                    targetIncident.getId().toString(),
                    provider + "-webhook",
                    "Created incident from " + provider + " alert: " + title
            );
        }

        auditService.record(
                AuditEventType.WEBHOOK_PROCESSED,
                "WebhookEvent",
                event.getId().toString(),
                provider + "-webhook",
                "Processed webhook alert for incident " + targetIncident.getId()
        );

        return new WebhookIngestionResponse(
                isNew ? "CREATED" : "UPDATED",
                isNew ? "Created new incident from webhook alert." : "Updated existing incident with webhook alert.",
                targetIncident.getId(),
                eventId,
                false
        );
    }

    private User getSystemUser() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN && u.getDeletedAt() == null)
                .findFirst()
                .orElseGet(() -> {
                    // Fallback to first available user
                    return userRepository.findAll().stream()
                            .filter(u -> u.getDeletedAt() == null)
                            .findFirst()
                            .orElse(null);
                });
    }
}

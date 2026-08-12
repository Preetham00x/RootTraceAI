package com.roottrace.integration;

import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.integration.dto.SlackCommandRequest;
import com.roottrace.integration.dto.SlackResponse;
import com.roottrace.intelligence.IncidentBriefingService;
import com.roottrace.intelligence.ServiceRiskService;
import com.roottrace.intelligence.dto.IncidentBriefingResponse;
import com.roottrace.intelligence.dto.ServiceRiskResponse;
import com.roottrace.user.Role;
import com.roottrace.user.User;
import com.roottrace.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SlackService implements ExternalNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SlackService.class);

    private final IncidentRepository incidentRepository;
    private final IncidentBriefingService incidentBriefingService;
    private final ServiceRiskService serviceRiskService;
    private final UserRepository userRepository;

    public SlackService(
            IncidentRepository incidentRepository,
            IncidentBriefingService incidentBriefingService,
            ServiceRiskService serviceRiskService,
            UserRepository userRepository) {
        this.incidentRepository = incidentRepository;
        this.incidentBriefingService = incidentBriefingService;
        this.serviceRiskService = serviceRiskService;
        this.userRepository = userRepository;
    }

    @Override
    public void sendIncidentAlert(Incident incident, String channel) {
        log.info("Sending Slack alert for incident [{}] to channel [{}]", incident.getId(), channel);
    }

    @Override
    public void sendIncidentBriefing(Incident incident, IncidentBriefingResponse briefing, String channel) {
        log.info("Sending Slack briefing for incident [{}] to channel [{}]", incident.getId(), channel);
    }

    @Transactional
    public SlackResponse handleSlackCommand(SlackCommandRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            return SlackResponse.ephemeral("Please provide an action: `/incident briefing <id>`, `/incident status <id>`, or `/incident create <title>`.");
        }

        String[] parts = request.text().trim().split("\\s+", 2);
        String action = parts[0].toLowerCase();
        String param = parts.length > 1 ? parts[1].trim() : "";

        return switch (action) {
            case "briefing" -> handleBriefingCommand(param);
            case "status" -> handleStatusCommand(param);
            case "create" -> handleCreateCommand(param, request.userName());
            default -> SlackResponse.ephemeral("Unknown command action `" + action + "`. Valid actions: `briefing`, `status`, `create`.");
        };
    }

    private SlackResponse handleBriefingCommand(String incidentIdStr) {
        if (incidentIdStr.isBlank()) {
            return SlackResponse.ephemeral("Usage: `/incident briefing <incidentId>`");
        }
        try {
            UUID id = UUID.fromString(incidentIdStr);
            Incident incident = incidentRepository.findByIdAndNotDeleted(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Incident", id));

            IncidentBriefingResponse briefing = incidentBriefingService.generateBriefing(id);
            ServiceRiskResponse risk = serviceRiskService.evaluateServiceRisk(incident.getService());

            String message = """
                    *🚨 Incident Briefing: %s*
                    *Service:* `%s` | *Severity:* `%s` | *Status:* `%s`
                    *Risk Tier:* `%s` (Score: %.1f)
                    *Recurring:* %s (Historical Recurrences: %d)

                    *Executive Summary:*
                    %s

                    *Recommended Triage Actions:*
                    %s
                    """.formatted(
                    incident.getTitle(),
                    incident.getService(),
                    incident.getSeverity(),
                    incident.getStatus(),
                    risk.riskTier(),
                    risk.riskScore(),
                    briefing.isRecurringIssue() ? "YES" : "NO",
                    briefing.recurrenceCount(),
                    briefing.executiveSummary(),
                    briefing.recommendedTriageActions().isEmpty() ? "_None specified_" :
                            String.join("\n• ", briefing.recommendedTriageActions())
            );

            return SlackResponse.inChannel(message);
        } catch (IllegalArgumentException ex) {
            return SlackResponse.ephemeral("Invalid incident UUID format: " + incidentIdStr);
        } catch (Exception ex) {
            return SlackResponse.ephemeral("Failed to fetch incident briefing: " + ex.getMessage());
        }
    }

    private SlackResponse handleStatusCommand(String incidentIdStr) {
        if (incidentIdStr.isBlank()) {
            return SlackResponse.ephemeral("Usage: `/incident status <incidentId>`");
        }
        try {
            UUID id = UUID.fromString(incidentIdStr);
            Incident incident = incidentRepository.findByIdAndNotDeleted(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Incident", id));

            String message = """
                    *Incident Status Report*
                    *ID:* `%s`
                    *Title:* %s
                    *Service:* `%s`
                    *Severity:* `%s`
                    *Status:* `%s`
                    *Created:* %s
                    *Resolution:* %s
                    """.formatted(
                    incident.getId(),
                    incident.getTitle(),
                    incident.getService(),
                    incident.getSeverity(),
                    incident.getStatus(),
                    incident.getCreatedAt(),
                    incident.getResolution() != null ? incident.getResolution() : "_In progress_"
            );

            return SlackResponse.inChannel(message);
        } catch (IllegalArgumentException ex) {
            return SlackResponse.ephemeral("Invalid incident UUID format: " + incidentIdStr);
        } catch (Exception ex) {
            return SlackResponse.ephemeral("Failed to fetch incident status: " + ex.getMessage());
        }
    }

    private SlackResponse handleCreateCommand(String title, String userName) {
        if (title.isBlank()) {
            return SlackResponse.ephemeral("Usage: `/incident create <title>`");
        }

        User systemUser = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN && u.getDeletedAt() == null)
                .findFirst()
                .orElse(null);

        Incident newInc = new Incident();
        newInc.setTitle(title);
        newInc.setDescription("Incident opened via Slack by @" + (userName != null ? userName : "user"));
        newInc.setService("general-service");
        newInc.setSeverity(IncidentSeverity.MEDIUM);
        newInc.setStatus(IncidentStatus.OPEN);
        newInc.setCreatedBy(systemUser);

        Incident saved = incidentRepository.save(newInc);

        return SlackResponse.inChannel(String.format("✅ Created new incident *[%s]*: `%s` (Severity: MEDIUM)",
                saved.getTitle(), saved.getId()));
    }
}

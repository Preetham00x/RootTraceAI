package com.roottrace.integration;

import com.roottrace.common.audit.AuditEventType;
import com.roottrace.common.audit.AuditService;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.common.security.CurrentUserService;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.integration.dto.CreateJiraTicketRequest;
import com.roottrace.integration.dto.JiraTicketResponse;
import com.roottrace.postmortem.Postmortem;
import com.roottrace.postmortem.PostmortemActionItem;
import com.roottrace.postmortem.PostmortemActionItemRepository;
import com.roottrace.postmortem.PostmortemRepository;
import com.roottrace.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class JiraService implements ExternalTicketService {

    private static final Logger log = LoggerFactory.getLogger(JiraService.class);
    private static final AtomicInteger TICKET_COUNTER = new AtomicInteger(1001);

    private final ExternalTicketRepository externalTicketRepository;
    private final IncidentRepository incidentRepository;
    private final PostmortemRepository postmortemRepository;
    private final PostmortemActionItemRepository actionItemRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public JiraService(
            ExternalTicketRepository externalTicketRepository,
            IncidentRepository incidentRepository,
            PostmortemRepository postmortemRepository,
            PostmortemActionItemRepository actionItemRepository,
            CurrentUserService currentUserService,
            AuditService auditService) {
        this.externalTicketRepository = externalTicketRepository;
        this.incidentRepository = incidentRepository;
        this.postmortemRepository = postmortemRepository;
        this.actionItemRepository = actionItemRepository;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    @Transactional
    public JiraTicketResponse createJiraTicket(
            UUID incidentId,
            UUID actionItemId,
            CreateJiraTicketRequest request) {

        Incident incident = incidentRepository.findByIdAndNotDeleted(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));

        Postmortem postmortem = postmortemRepository.findByIncidentId(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Postmortem for incident", incidentId));

        PostmortemActionItem actionItem = actionItemRepository.findByIdAndPostmortemId(actionItemId, postmortem.getId())
                .orElseThrow(() -> new ResourceNotFoundException("PostmortemActionItem", actionItemId));

        return createTicketForActionItem(incident, actionItem, request);
    }

    @Override
    @Transactional
    public JiraTicketResponse createTicketForActionItem(
            Incident incident,
            PostmortemActionItem actionItem,
            CreateJiraTicketRequest request) {

        Optional<ExternalTicket> existingOpt = externalTicketRepository.findByProviderAndActionItemId("JIRA", actionItem.getId());
        if (existingOpt.isPresent()) {
            ExternalTicket existing = existingOpt.get();
            throw new BadRequestException("Jira ticket already exists for this action item: " + existing.getExternalTicketId());
        }

        String projectKey = (request != null && request.projectKey() != null && !request.projectKey().isBlank())
                ? request.projectKey().toUpperCase().trim() : "SRE";

        String ticketKey = projectKey + "-" + TICKET_COUNTER.incrementAndGet();
        String jiraBaseUrl = "https://jira.company.internal/browse/" + ticketKey;

        log.info("Created Jira ticket [{}] for action item [{}] (Incident: {})", ticketKey, actionItem.getId(), incident.getId());

        ExternalTicket ticket = new ExternalTicket(
                incident,
                actionItem,
                "JIRA",
                ticketKey,
                jiraBaseUrl
        );

        ExternalTicket saved = externalTicketRepository.save(ticket);
        User currentUser = currentUserService.getCurrentUser();

        auditService.record(
                AuditEventType.JIRA_TICKET_CREATED,
                "ExternalTicket",
                saved.getId().toString(),
                currentUser != null ? currentUser.getEmail() : "system",
                "Created Jira ticket " + ticketKey + " for action item " + actionItem.getTitle()
        );

        return new JiraTicketResponse(
                saved.getId(),
                incident.getId(),
                actionItem.getId(),
                saved.getProvider(),
                saved.getExternalTicketId(),
                saved.getExternalUrl(),
                saved.getStatus()
        );
    }
}

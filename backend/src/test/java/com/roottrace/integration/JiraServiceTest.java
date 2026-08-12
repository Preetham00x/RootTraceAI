package com.roottrace.integration;

import com.roottrace.common.audit.AuditService;
import com.roottrace.common.audit.AuditEventType;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.common.security.CurrentUserService;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.integration.dto.CreateJiraTicketRequest;
import com.roottrace.integration.dto.JiraTicketResponse;
import com.roottrace.postmortem.ActionItemCategory;
import com.roottrace.postmortem.ActionItemPriority;
import com.roottrace.postmortem.Postmortem;
import com.roottrace.postmortem.PostmortemActionItem;
import com.roottrace.postmortem.PostmortemActionItemRepository;
import com.roottrace.postmortem.PostmortemRepository;
import com.roottrace.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JiraServiceTest {

    private ExternalTicketRepository externalTicketRepository;
    private IncidentRepository incidentRepository;
    private PostmortemRepository postmortemRepository;
    private PostmortemActionItemRepository actionItemRepository;
    private CurrentUserService currentUserService;
    private AuditService auditService;
    private JiraService jiraService;

    private Incident incident;
    private Postmortem postmortem;
    private PostmortemActionItem actionItem;

    private final UUID incidentId = UUID.randomUUID();
    private final UUID actionItemId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        externalTicketRepository = mock(ExternalTicketRepository.class);
        incidentRepository = mock(IncidentRepository.class);
        postmortemRepository = mock(PostmortemRepository.class);
        actionItemRepository = mock(PostmortemActionItemRepository.class);
        currentUserService = mock(CurrentUserService.class);
        auditService = mock(AuditService.class);

        User user = mock(User.class);
        when(user.getEmail()).thenReturn("sre@roottrace.com");
        when(currentUserService.getCurrentUser()).thenReturn(user);

        incident = mock(Incident.class);
        when(incident.getId()).thenReturn(incidentId);
        when(incidentRepository.findByIdAndNotDeleted(incidentId)).thenReturn(Optional.of(incident));

        postmortem = mock(Postmortem.class);
        when(postmortem.getId()).thenReturn(UUID.randomUUID());
        when(postmortemRepository.findByIncidentId(incidentId)).thenReturn(Optional.of(postmortem));

        actionItem = mock(PostmortemActionItem.class);
        when(actionItem.getId()).thenReturn(actionItemId);
        when(actionItem.getTitle()).thenReturn("Add pool saturation alert");
        when(actionItemRepository.findByIdAndPostmortemId(actionItemId, postmortem.getId()))
                .thenReturn(Optional.of(actionItem));

        jiraService = new JiraService(
                externalTicketRepository,
                incidentRepository,
                postmortemRepository,
                actionItemRepository,
                currentUserService,
                auditService
        );
    }

    @Test
    @DisplayName("Should create Jira ticket for postmortem action item and audit event")
    void testCreateJiraTicket_Success() {
        when(externalTicketRepository.findByProviderAndActionItemId("JIRA", actionItemId))
                .thenReturn(Optional.empty());

        when(externalTicketRepository.save(any(ExternalTicket.class))).thenAnswer(inv -> {
            ExternalTicket t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        CreateJiraTicketRequest request = new CreateJiraTicketRequest("SRE", "Task", "Summary", "Description");
        JiraTicketResponse response = jiraService.createJiraTicket(incidentId, actionItemId, request);

        assertThat(response).isNotNull();
        assertThat(response.incidentId()).isEqualTo(incidentId);
        assertThat(response.actionItemId()).isEqualTo(actionItemId);
        assertThat(response.provider()).isEqualTo("JIRA");
        assertThat(response.externalTicketId()).startsWith("SRE-");
        assertThat(response.externalUrl()).contains("https://jira.company.internal/browse/SRE-");

        verify(auditService).record(
                eq(AuditEventType.JIRA_TICKET_CREATED),
                eq("ExternalTicket"),
                any(),
                eq("sre@roottrace.com"),
                any()
        );
    }

    @Test
    @DisplayName("Should throw BadRequestException if Jira ticket already exists for action item")
    void testCreateJiraTicket_DuplicateRejection() {
        ExternalTicket existing = new ExternalTicket(incident, actionItem, "JIRA", "SRE-1005", "http://jira/SRE-1005");
        when(externalTicketRepository.findByProviderAndActionItemId("JIRA", actionItemId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> jiraService.createJiraTicket(incidentId, actionItemId, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Jira ticket already exists");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException if incident does not exist")
    void testCreateJiraTicket_MissingIncident() {
        UUID unknownId = UUID.randomUUID();
        when(incidentRepository.findByIdAndNotDeleted(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jiraService.createJiraTicket(unknownId, actionItemId, null))
                .isInstanceOf(com.roottrace.common.exception.ResourceNotFoundException.class);
    }
}

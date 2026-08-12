package com.roottrace.postmortem;

import com.roottrace.ai.diagnosis.AiDiagnosis;
import com.roottrace.ai.diagnosis.AiDiagnosisRepository;
import com.roottrace.common.audit.AuditService;
import com.roottrace.common.audit.AuditEventType;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.common.security.CurrentUserService;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.investigation.InvestigationPlan;
import com.roottrace.investigation.InvestigationPlanRepository;
import com.roottrace.investigation.InvestigationStep;
import com.roottrace.investigation.InvestigationStepStatus;
import com.roottrace.postmortem.dto.CreateActionItemRequest;
import com.roottrace.postmortem.dto.PostmortemActionItemResponse;
import com.roottrace.postmortem.dto.PostmortemAiResponse;
import com.roottrace.postmortem.dto.PostmortemResponse;
import com.roottrace.postmortem.dto.PostmortemTimelineEntry;
import com.roottrace.postmortem.dto.UpdateActionItemRequest;
import com.roottrace.postmortem.dto.UpdatePostmortemRequest;
import com.roottrace.user.Role;
import com.roottrace.user.User;
import com.roottrace.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostmortemServiceTest {

    private IncidentRepository incidentRepository;
    private AiDiagnosisRepository diagnosisRepository;
    private InvestigationPlanRepository investigationPlanRepository;
    private PostmortemRepository postmortemRepository;
    private PostmortemActionItemRepository actionItemRepository;
    private UserRepository userRepository;
    private GeminiPostmortemService geminiPostmortemService;
    private CurrentUserService currentUserService;
    private AuditService auditService;
    private PostmortemService postmortemService;

    private User testUser;
    private Incident testIncident;
    private AiDiagnosis testDiagnosis;

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        diagnosisRepository = mock(AiDiagnosisRepository.class);
        investigationPlanRepository = mock(InvestigationPlanRepository.class);
        postmortemRepository = mock(PostmortemRepository.class);
        actionItemRepository = mock(PostmortemActionItemRepository.class);
        userRepository = mock(UserRepository.class);
        geminiPostmortemService = mock(GeminiPostmortemService.class);
        currentUserService = mock(CurrentUserService.class);
        auditService = mock(AuditService.class);

        testUser = mock(User.class);
        when(testUser.getId()).thenReturn(UUID.randomUUID());
        when(testUser.getEmail()).thenReturn("sre@roottrace.com");
        when(testUser.getFirstName()).thenReturn("Alice");
        when(testUser.getLastName()).thenReturn("Smith");
        when(testUser.getRole()).thenReturn(Role.ENGINEER);

        testIncident = mock(Incident.class);
        when(testIncident.getId()).thenReturn(UUID.randomUUID());
        when(testIncident.getTitle()).thenReturn("Database Connection Exhaustion");
        when(testIncident.getService()).thenReturn("order-service");
        when(testIncident.getSeverity()).thenReturn(IncidentSeverity.CRITICAL);
        when(testIncident.getStatus()).thenReturn(IncidentStatus.RESOLVED);
        when(testIncident.getCreatedAt()).thenReturn(Instant.parse("2026-08-12T10:00:00Z"));
        when(testIncident.getResolvedAt()).thenReturn(Instant.parse("2026-08-12T11:00:00Z"));
        when(testIncident.getDescription()).thenReturn("HikariPool connection timeout");
        when(testIncident.getResolution()).thenReturn("Increased max pool size to 50");
        when(testIncident.isDeleted()).thenReturn(false);

        testDiagnosis = mock(AiDiagnosis.class);
        when(testDiagnosis.getId()).thenReturn(UUID.randomUUID());
        when(testDiagnosis.getProbableRootCause()).thenReturn("Pool size exhaustion");
        when(testDiagnosis.getCreatedAt()).thenReturn(Instant.parse("2026-08-12T10:10:00Z"));

        when(currentUserService.getCurrentUser()).thenReturn(testUser);

        postmortemService = new PostmortemService(
                incidentRepository,
                diagnosisRepository,
                investigationPlanRepository,
                postmortemRepository,
                actionItemRepository,
                userRepository,
                geminiPostmortemService,
                currentUserService,
                auditService
        );
    }

    @Test
    @DisplayName("Should generate postmortem from incident, diagnosis, and investigation plans")
    void testGeneratePostmortem_Success() {
        UUID incidentId = testIncident.getId();

        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(testIncident));
        when(postmortemRepository.findByIncidentId(incidentId)).thenReturn(Optional.empty());
        when(diagnosisRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId)).thenReturn(List.of(testDiagnosis));

        InvestigationPlan plan = mock(InvestigationPlan.class);
        when(plan.getTitle()).thenReturn("Triage Runbook");
        InvestigationStep step = mock(InvestigationStep.class);
        when(step.getTitle()).thenReturn("Inspect Metrics");
        when(step.getStatus()).thenReturn(InvestigationStepStatus.COMPLETED);
        when(step.getCompletedAt()).thenReturn(Instant.parse("2026-08-12T10:30:00Z"));
        when(plan.getSteps()).thenReturn(List.of(step));
        when(investigationPlanRepository.findByIncidentIdWithSteps(incidentId)).thenReturn(List.of(plan));

        PostmortemAiResponse aiResponse = new PostmortemAiResponse(
                "Postmortem: Database Connection Exhaustion",
                "Incident lasted 60 minutes affecting checkout.",
                "15% of checkout requests failed.",
                "Pool exhausted due to unindexed query.",
                "Increased pool size and added DB index.",
                List.of("Pool metrics were missing alert", "Hotfix deployment was rapid"),
                List.of(new PostmortemAiResponse.ProposedActionItem(
                        "Add pool saturation alert",
                        "Alert when pool usage > 80%",
                        "DETECT",
                        "HIGH"
                ))
        );

        when(geminiPostmortemService.generatePostmortem(eq(testIncident), eq(testDiagnosis), any(), any()))
                .thenReturn(aiResponse);

        when(postmortemRepository.save(any(Postmortem.class))).thenAnswer(inv -> {
            Postmortem p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });

        PostmortemResponse response = postmortemService.generatePostmortem(incidentId);

        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Postmortem: Database Connection Exhaustion");
        assertThat(response.downtimeMinutes()).isEqualTo(60L);
        assertThat(response.summary()).contains("Incident lasted 60 minutes");
        assertThat(response.lessonsLearned()).hasSize(2);
        assertThat(response.actionItems()).hasSize(1);
        assertThat(response.actionItems().get(0).category()).isEqualTo(ActionItemCategory.DETECT);
        assertThat(response.actionItems().get(0).priority()).isEqualTo(ActionItemPriority.HIGH);
        assertThat(response.timeline()).isNotEmpty();

        verify(auditService).record(
                eq(AuditEventType.POSTMORTEM_GENERATED),
                eq("Postmortem"),
                any(),
                eq("sre@roottrace.com"),
                any()
        );
    }

    @Test
    @DisplayName("Should throw BadRequestException if incident is not resolved or closed")
    void testGeneratePostmortem_IncidentNotResolved() {
        UUID incidentId = testIncident.getId();
        when(testIncident.getStatus()).thenReturn(IncidentStatus.INVESTIGATING);
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(testIncident));

        assertThatThrownBy(() -> postmortemService.generatePostmortem(incidentId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Incident must be RESOLVED or CLOSED");
    }

    @Test
    @DisplayName("Should throw BadRequestException if postmortem already exists for incident")
    void testGeneratePostmortem_AlreadyExists() {
        UUID incidentId = testIncident.getId();
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(testIncident));
        when(postmortemRepository.findByIncidentId(incidentId)).thenReturn(Optional.of(mock(Postmortem.class)));

        assertThatThrownBy(() -> postmortemService.generatePostmortem(incidentId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("A postmortem already exists");
    }

    @Test
    @DisplayName("Should retrieve postmortem for incident")
    void testGetPostmortem_Success() {
        UUID incidentId = testIncident.getId();
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(testIncident));

        Postmortem postmortem = new Postmortem(
                testIncident, "Postmortem Title", "Summary", "Impact",
                "Root Cause", "Resolution", List.of(), List.of(), 60L, testUser
        );
        postmortem.setId(UUID.randomUUID());

        when(postmortemRepository.findByIncidentIdWithActionItems(incidentId)).thenReturn(Optional.of(postmortem));

        PostmortemResponse response = postmortemService.getPostmortem(incidentId);

        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Postmortem Title");
    }

    @Test
    @DisplayName("Should update postmortem and set publishedAt when transitioned to PUBLISHED")
    void testUpdatePostmortem_Publish() {
        UUID incidentId = testIncident.getId();
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(testIncident));

        Postmortem postmortem = new Postmortem(
                testIncident, "Draft Title", "Summary", "Impact",
                "Root Cause", "Resolution", List.of(), List.of(), 60L, testUser
        );
        postmortem.setId(UUID.randomUUID());
        postmortem.setStatus(PostmortemStatus.DRAFT);

        when(postmortemRepository.findByIncidentIdWithActionItems(incidentId)).thenReturn(Optional.of(postmortem));
        when(postmortemRepository.save(any(Postmortem.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdatePostmortemRequest request = new UpdatePostmortemRequest(
                "Final Title",
                "Updated Summary",
                null, null, null, null, null,
                PostmortemStatus.PUBLISHED
        );

        PostmortemResponse response = postmortemService.updatePostmortem(incidentId, request);

        assertThat(response.title()).isEqualTo("Final Title");
        assertThat(response.summary()).isEqualTo("Updated Summary");
        assertThat(response.status()).isEqualTo(PostmortemStatus.PUBLISHED);
        assertThat(response.publishedAt()).isNotNull();

        verify(auditService).record(
                eq(AuditEventType.POSTMORTEM_PUBLISHED),
                eq("Postmortem"),
                any(),
                eq("sre@roottrace.com"),
                any()
        );
    }

    @Test
    @DisplayName("Should create action item for postmortem")
    void testCreateActionItem_Success() {
        UUID incidentId = testIncident.getId();
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(testIncident));

        Postmortem postmortem = new Postmortem(
                testIncident, "Title", "Summary", "Impact",
                "Root Cause", "Resolution", List.of(), List.of(), 60L, testUser
        );
        postmortem.setId(UUID.randomUUID());

        when(postmortemRepository.findByIncidentIdWithActionItems(incidentId)).thenReturn(Optional.of(postmortem));
        when(actionItemRepository.save(any(PostmortemActionItem.class))).thenAnswer(inv -> {
            PostmortemActionItem item = inv.getArgument(0);
            if (item.getId() == null) {
                item.setId(UUID.randomUUID());
            }
            return item;
        });

        CreateActionItemRequest request = new CreateActionItemRequest(
                "Configure Circuit Breaker",
                "Set 50% failure threshold",
                ActionItemCategory.PREVENT,
                ActionItemPriority.CRITICAL,
                null,
                Instant.parse("2026-08-20T00:00:00Z")
        );

        PostmortemActionItemResponse response = postmortemService.createActionItem(incidentId, request);

        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Configure Circuit Breaker");
        assertThat(response.category()).isEqualTo(ActionItemCategory.PREVENT);
        assertThat(response.priority()).isEqualTo(ActionItemPriority.CRITICAL);
        assertThat(response.status()).isEqualTo(ActionItemStatus.OPEN);

        verify(auditService).record(
                eq(AuditEventType.POSTMORTEM_ACTION_ITEM_CREATED),
                eq("PostmortemActionItem"),
                any(),
                eq("sre@roottrace.com"),
                any()
        );
    }

    @Test
    @DisplayName("Should update action item and record completedAt on COMPLETED status")
    void testUpdateActionItem_Completed() {
        UUID incidentId = testIncident.getId();
        UUID actionItemId = UUID.randomUUID();
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(testIncident));

        Postmortem postmortem = new Postmortem(
                testIncident, "Title", "Summary", "Impact",
                "Root Cause", "Resolution", List.of(), List.of(), 60L, testUser
        );
        postmortem.setId(UUID.randomUUID());

        PostmortemActionItem item = new PostmortemActionItem(
                postmortem, "Fix Bug", "Patch leak", ActionItemCategory.PREVENT, ActionItemPriority.HIGH, null, null
        );
        item.setId(actionItemId);
        item.setStatus(ActionItemStatus.IN_PROGRESS);

        when(postmortemRepository.findByIncidentIdWithActionItems(incidentId)).thenReturn(Optional.of(postmortem));
        when(actionItemRepository.findByIdAndPostmortemId(actionItemId, postmortem.getId())).thenReturn(Optional.of(item));
        when(actionItemRepository.save(any(PostmortemActionItem.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateActionItemRequest request = new UpdateActionItemRequest(
                null, "Hotfix merged to main", null, null, ActionItemStatus.COMPLETED, null, null
        );

        PostmortemActionItemResponse response = postmortemService.updateActionItem(incidentId, actionItemId, request);

        assertThat(response.status()).isEqualTo(ActionItemStatus.COMPLETED);
        assertThat(response.description()).isEqualTo("Hotfix merged to main");
        assertThat(response.completedAt()).isNotNull();

        verify(auditService).record(
                eq(AuditEventType.POSTMORTEM_ACTION_ITEM_UPDATED),
                eq("PostmortemActionItem"),
                any(),
                eq("sre@roottrace.com"),
                any()
        );
    }

    @Test
    @DisplayName("Should export formatted Markdown document")
    void testExportMarkdown_Success() {
        UUID incidentId = testIncident.getId();
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(testIncident));

        Postmortem postmortem = new Postmortem(
                testIncident, "Database Outage Postmortem", "The database ran out of connections.",
                "Checkout was unavailable for 60 minutes.", "Query timeout allowed connections to stay open.",
                "Restarted database and increased pool size.",
                List.of(new PostmortemTimelineEntry(Instant.parse("2026-08-12T10:00:00Z"), "Incident detected", "INCIDENT_CREATION")),
                List.of("Need better pool metrics"),
                60L,
                testUser
        );
        postmortem.setId(UUID.randomUUID());

        PostmortemActionItem item = new PostmortemActionItem(
                postmortem, "Add Prometheus alert", "Alert on pool saturation",
                ActionItemCategory.DETECT, ActionItemPriority.HIGH, testUser, Instant.parse("2026-08-25T00:00:00Z")
        );
        postmortem.addActionItem(item);

        when(postmortemRepository.findByIncidentIdWithActionItems(incidentId)).thenReturn(Optional.of(postmortem));

        String markdown = postmortemService.exportMarkdown(incidentId);

        assertThat(markdown).contains("# Postmortem: Database Outage Postmortem");
        assertThat(markdown).contains("## Executive Summary");
        assertThat(markdown).contains("The database ran out of connections.");
        assertThat(markdown).contains("## Impact Assessment");
        assertThat(markdown).contains("## Root Cause Analysis");
        assertThat(markdown).contains("## Resolution & Recovery");
        assertThat(markdown).contains("## Chronological Timeline");
        assertThat(markdown).contains("Incident detected");
        assertThat(markdown).contains("## Lessons Learned");
        assertThat(markdown).contains("Need better pool metrics");
        assertThat(markdown).contains("## Preventive Action Items");
        assertThat(markdown).contains("Add Prometheus alert");
        assertThat(markdown).contains("sre@roottrace.com");
    }
}

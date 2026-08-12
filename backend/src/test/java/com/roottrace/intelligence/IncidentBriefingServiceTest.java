package com.roottrace.intelligence;

import com.roottrace.ai.diagnosis.AiDiagnosis;
import com.roottrace.ai.diagnosis.AiDiagnosisRepository;
import com.roottrace.ai.diagnosis.DiagnosisException;
import com.roottrace.ai.exception.AiServiceException;
import com.roottrace.common.audit.AuditService;
import com.roottrace.common.audit.AuditEventType;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.common.security.CurrentUserService;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.intelligence.dto.CorrelatedIncidentResponse;
import com.roottrace.intelligence.dto.IncidentBriefingAiResponse;
import com.roottrace.intelligence.dto.IncidentBriefingResponse;
import com.roottrace.intelligence.dto.RelatedIncidentsResponse;
import com.roottrace.investigation.InvestigationPlan;
import com.roottrace.investigation.InvestigationPlanRepository;
import com.roottrace.postmortem.Postmortem;
import com.roottrace.postmortem.PostmortemRepository;
import com.roottrace.user.Role;
import com.roottrace.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IncidentBriefingServiceTest {

    private IncidentRepository incidentRepository;
    private IncidentCorrelationService correlationService;
    private AiDiagnosisRepository diagnosisRepository;
    private InvestigationPlanRepository investigationPlanRepository;
    private PostmortemRepository postmortemRepository;
    private GeminiBriefingService geminiBriefingService;
    private CurrentUserService currentUserService;
    private AuditService auditService;
    private IncidentBriefingService briefingService;

    private User testUser;
    private Incident targetIncident;
    private final UUID targetIncidentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        correlationService = mock(IncidentCorrelationService.class);
        diagnosisRepository = mock(AiDiagnosisRepository.class);
        investigationPlanRepository = mock(InvestigationPlanRepository.class);
        postmortemRepository = mock(PostmortemRepository.class);
        geminiBriefingService = mock(GeminiBriefingService.class);
        currentUserService = mock(CurrentUserService.class);
        auditService = mock(AuditService.class);

        testUser = mock(User.class);
        when(testUser.getEmail()).thenReturn("sre@roottrace.com");
        when(currentUserService.getCurrentUser()).thenReturn(testUser);

        targetIncident = mock(Incident.class);
        when(targetIncident.getId()).thenReturn(targetIncidentId);
        when(targetIncident.getTitle()).thenReturn("High Latency on Checkout");
        when(targetIncident.getService()).thenReturn("payment-service");
        when(targetIncident.getSeverity()).thenReturn(IncidentSeverity.CRITICAL);
        when(targetIncident.getStatus()).thenReturn(IncidentStatus.OPEN);
        when(targetIncident.getDescription()).thenReturn("HTTP 504 gateway timeouts");
        when(targetIncident.isDeleted()).thenReturn(false);

        when(incidentRepository.findById(targetIncidentId)).thenReturn(Optional.of(targetIncident));

        briefingService = new IncidentBriefingService(
                incidentRepository,
                correlationService,
                diagnosisRepository,
                investigationPlanRepository,
                postmortemRepository,
                geminiBriefingService,
                currentUserService,
                auditService
        );
    }

    @Test
    @DisplayName("Should generate SRE intelligence briefing with historical diagnoses, plans, and postmortems")
    void testGenerateBriefing_Success() {
        UUID histIncId = UUID.randomUUID();
        CorrelatedIncidentResponse hist = new CorrelatedIncidentResponse(
                histIncId, "Past Timeout", "payment-service", IncidentSeverity.HIGH,
                IncidentStatus.RESOLVED, Instant.now(), Instant.now(), "Restarted pods",
                0.92, true, 12.0, 0.94, true, "Same service recurrence"
        );

        when(correlationService.findRelatedIncidents(eq(targetIncidentId), anyInt(), anyDouble(), anyBoolean()))
                .thenReturn(new RelatedIncidentsResponse(targetIncidentId, 1, true, List.of(hist)));

        AiDiagnosis diag = mock(AiDiagnosis.class);
        when(diag.getProbableRootCause()).thenReturn("Connection pool leak");
        when(diagnosisRepository.findByIncidentIdOrderByCreatedAtDesc(histIncId)).thenReturn(List.of(diag));

        InvestigationPlan plan = mock(InvestigationPlan.class);
        when(investigationPlanRepository.findByIncidentIdWithSteps(histIncId)).thenReturn(List.of(plan));

        Postmortem pm = mock(Postmortem.class);
        when(postmortemRepository.findByIncidentIdWithActionItems(histIncId)).thenReturn(Optional.of(pm));

        IncidentBriefingAiResponse aiResponse = new IncidentBriefingAiResponse(
                "Incident indicates recurring connection pool leak in payment-service.",
                true,
                2,
                List.of("Check active connections in PostgreSQL", "Scale payment worker replicas"),
                List.of("Connection pool leak"),
                List.of("Inspect thread dump for waiting connections"),
                List.of("Connection pool threshold should be set to 80%"),
                List.of("Add circuit breaker on Stripe client")
        );

        when(geminiBriefingService.generateBriefing(any(), any(), any(), any(), any()))
                .thenReturn(aiResponse);

        IncidentBriefingResponse response = briefingService.generateBriefing(targetIncidentId);

        assertThat(response).isNotNull();
        assertThat(response.incidentId()).isEqualTo(targetIncidentId);
        assertThat(response.executiveSummary()).contains("recurring connection pool leak");
        assertThat(response.isRecurringIssue()).isTrue();
        assertThat(response.recommendedTriageActions()).hasSize(2);
        assertThat(response.historicalRootCauses()).contains("Connection pool leak");
        assertThat(response.topCorrelatedIncidents()).hasSize(1);

        verify(auditService).record(
                eq(AuditEventType.AI_INCIDENT_BRIEFING_GENERATED),
                eq("Incident"),
                eq(targetIncidentId.toString()),
                eq("sre@roottrace.com"),
                any()
        );
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when target incident is missing")
    void testGenerateBriefing_NotFound() {
        UUID unknownId = UUID.randomUUID();
        when(incidentRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> briefingService.generateBriefing(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

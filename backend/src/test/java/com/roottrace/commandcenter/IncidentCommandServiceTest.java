package com.roottrace.commandcenter;

import com.roottrace.ai.diagnosis.AiDiagnosisRepository;
import com.roottrace.commandcenter.dto.IncidentCommandResponse;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.integration.ExternalTicketRepository;
import com.roottrace.integration.RunbookExecutionRepository;
import com.roottrace.intelligence.IncidentCorrelationService;
import com.roottrace.intelligence.dto.RelatedIncidentsResponse;
import com.roottrace.investigation.InvestigationPlanRepository;
import com.roottrace.postmortem.PostmortemActionItemRepository;
import com.roottrace.postmortem.PostmortemRepository;
import com.roottrace.slo.BurnRateService;
import com.roottrace.slo.ErrorBudgetService;
import com.roottrace.slo.SloEvaluationService;
import com.roottrace.slo.SloRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IncidentCommandServiceTest {

    private IncidentRepository incidentRepository;
    private AiDiagnosisRepository diagnosisRepository;
    private InvestigationPlanRepository investigationPlanRepository;
    private IncidentCorrelationService correlationService;
    private PostmortemRepository postmortemRepository;
    private PostmortemActionItemRepository actionItemRepository;
    private SloRepository sloRepository;
    private SloEvaluationService sloEvaluationService;
    private ErrorBudgetService errorBudgetService;
    private BurnRateService burnRateService;
    private RunbookExecutionRepository runbookExecutionRepository;
    private ExternalTicketRepository externalTicketRepository;
    private IncidentCommandService commandService;

    private final UUID incidentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        diagnosisRepository = mock(AiDiagnosisRepository.class);
        investigationPlanRepository = mock(InvestigationPlanRepository.class);
        correlationService = mock(IncidentCorrelationService.class);
        postmortemRepository = mock(PostmortemRepository.class);
        actionItemRepository = mock(PostmortemActionItemRepository.class);
        sloRepository = mock(SloRepository.class);
        sloEvaluationService = mock(SloEvaluationService.class);
        errorBudgetService = mock(ErrorBudgetService.class);
        burnRateService = mock(BurnRateService.class);
        runbookExecutionRepository = mock(RunbookExecutionRepository.class);
        externalTicketRepository = mock(ExternalTicketRepository.class);

        commandService = new IncidentCommandService(
                incidentRepository,
                diagnosisRepository,
                investigationPlanRepository,
                correlationService,
                postmortemRepository,
                actionItemRepository,
                sloRepository,
                sloEvaluationService,
                errorBudgetService,
                burnRateService,
                runbookExecutionRepository,
                externalTicketRepository,
                new ReliabilityRecommendationService()
        );
    }

    @Test
    @DisplayName("Should aggregate unified incident command picture")
    void testGetIncidentCommandDetails_Success() {
        Incident inc = mock(Incident.class);
        when(inc.getId()).thenReturn(incidentId);
        when(inc.getTitle()).thenReturn("Checkout 500 error spike");
        when(inc.getService()).thenReturn("payment-service");
        when(inc.getSeverity()).thenReturn(IncidentSeverity.CRITICAL);
        when(inc.getStatus()).thenReturn(IncidentStatus.INVESTIGATING);
        when(inc.getCreatedAt()).thenReturn(Instant.now().minusSeconds(1200));

        when(incidentRepository.findByIdAndNotDeleted(incidentId)).thenReturn(Optional.of(inc));
        when(diagnosisRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId)).thenReturn(List.of());
        when(correlationService.findRelatedIncidents(eq(incidentId), any(), any(), any()))
                .thenReturn(new RelatedIncidentsResponse(incidentId, 0, false, List.of()));
        when(postmortemRepository.findByIncidentIdWithActionItems(incidentId)).thenReturn(Optional.empty());
        when(sloRepository.findByServiceNameAndEnabledTrue("payment-service")).thenReturn(List.of());
        when(runbookExecutionRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId)).thenReturn(List.of());
        when(externalTicketRepository.findByIncidentId(incidentId)).thenReturn(List.of());

        IncidentCommandResponse response = commandService.getIncidentCommandDetails(incidentId);

        assertThat(response).isNotNull();
        assertThat(response.incident()).isNotNull();
        assertThat(response.incident().title()).isEqualTo("Checkout 500 error spike");
        assertThat(response.timelineEvents()).isNotEmpty();
        assertThat(response.recommendations()).isNotEmpty();
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when incident does not exist")
    void testGetIncidentCommandDetails_NotFound() {
        when(incidentRepository.findByIdAndNotDeleted(incidentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commandService.getIncidentCommandDetails(incidentId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

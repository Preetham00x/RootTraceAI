package com.roottrace.commandcenter;

import com.roottrace.commandcenter.dto.ReliabilityEventsResponse;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.integration.RunbookExecution;
import com.roottrace.integration.RunbookExecutionRepository;
import com.roottrace.integration.RunbookExecutionStatus;
import com.roottrace.postmortem.ActionItemStatus;
import com.roottrace.postmortem.Postmortem;
import com.roottrace.postmortem.PostmortemActionItem;
import com.roottrace.postmortem.PostmortemActionItemRepository;
import com.roottrace.postmortem.PostmortemRepository;
import com.roottrace.slo.ErrorBudgetService;
import com.roottrace.slo.Slo;
import com.roottrace.slo.SloEvaluationService;
import com.roottrace.slo.SloRepository;
import com.roottrace.slo.SloStatus;
import com.roottrace.slo.dto.ErrorBudgetResponse;
import com.roottrace.slo.dto.SloEvaluationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReliabilityEventFeedServiceTest {

    private IncidentRepository incidentRepository;
    private SloRepository sloRepository;
    private SloEvaluationService sloEvaluationService;
    private ErrorBudgetService errorBudgetService;
    private PostmortemRepository postmortemRepository;
    private PostmortemActionItemRepository actionItemRepository;
    private RunbookExecutionRepository runbookExecutionRepository;
    private ReliabilityEventFeedService eventFeedService;

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        sloRepository = mock(SloRepository.class);
        sloEvaluationService = mock(SloEvaluationService.class);
        errorBudgetService = mock(ErrorBudgetService.class);
        postmortemRepository = mock(PostmortemRepository.class);
        actionItemRepository = mock(PostmortemActionItemRepository.class);
        runbookExecutionRepository = mock(RunbookExecutionRepository.class);

        eventFeedService = new ReliabilityEventFeedService(
                incidentRepository,
                sloRepository,
                sloEvaluationService,
                errorBudgetService,
                postmortemRepository,
                actionItemRepository,
                runbookExecutionRepository
        );
    }

    @Test
    @DisplayName("Should aggregate incident and SLO events chronologically")
    void testGetEventFeed_Success() {
        Incident inc = mock(Incident.class);
        when(inc.getId()).thenReturn(UUID.randomUUID());
        when(inc.getTitle()).thenReturn("Checkout Failure");
        when(inc.getService()).thenReturn("payment-service");
        when(inc.getSeverity()).thenReturn(IncidentSeverity.CRITICAL);
        when(inc.getStatus()).thenReturn(IncidentStatus.OPEN);
        when(inc.getCreatedAt()).thenReturn(Instant.now().minusSeconds(1800));

        when(incidentRepository.findAllNotDeleted(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(inc)));

        Slo slo = mock(Slo.class);
        when(slo.getId()).thenReturn(UUID.randomUUID());
        when(slo.getName()).thenReturn("Availability");
        when(slo.getServiceName()).thenReturn("payment-service");
        when(slo.getEnabled()).thenReturn(true);
        when(sloRepository.findAll()).thenReturn(List.of(slo));

        SloEvaluationResponse eval = new SloEvaluationResponse(
                UUID.randomUUID(), "payment-service", "Availability", 99.9, 99.0, -0.9,
                SloStatus.BREACHED, 1000L, 990L, 10L, 0.0, 100.0, Instant.now().minusSeconds(600)
        );
        when(sloEvaluationService.evaluateSlo(slo)).thenReturn(eval);
        when(errorBudgetService.calculateErrorBudget(slo)).thenReturn(mock(ErrorBudgetResponse.class));

        when(postmortemRepository.findAll()).thenReturn(List.of());
        when(actionItemRepository.findAll()).thenReturn(List.of());
        when(runbookExecutionRepository.findAll()).thenReturn(List.of());

        ReliabilityEventsResponse response = eventFeedService.getEventFeed(null, 30, 20);

        assertThat(response).isNotNull();
        assertThat(response.count()).isEqualTo(2);
        assertThat(response.events().get(0).type()).isEqualTo("SLO_BREACH");
        assertThat(response.events().get(1).type()).isEqualTo("INCIDENT_CREATED");
    }

    @Test
    @DisplayName("Should reject invalid days or limit parameters")
    void testGetEventFeed_Validation() {
        assertThatThrownBy(() -> eventFeedService.getEventFeed(null, 0, 10))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("days parameter must be between 1 and 365");

        assertThatThrownBy(() -> eventFeedService.getEventFeed(null, 30, 200))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("limit parameter must be between 1 and 100");
    }
}

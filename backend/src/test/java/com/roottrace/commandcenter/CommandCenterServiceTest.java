package com.roottrace.commandcenter;

import com.roottrace.commandcenter.dto.CommandCenterOverviewResponse;
import com.roottrace.commandcenter.dto.ReliabilityPenaltyResponse;
import com.roottrace.commandcenter.dto.ReliabilityScoreResponse;
import com.roottrace.commandcenter.dto.ServiceHealthSummaryResponse;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.integration.RunbookExecutionRepository;
import com.roottrace.intelligence.SreMetricsService;
import com.roottrace.intelligence.dto.SreMetricsSummaryResponse;
import com.roottrace.postmortem.PostmortemActionItemRepository;
import com.roottrace.slo.ErrorBudgetService;
import com.roottrace.slo.ReliabilityTrendService;
import com.roottrace.slo.SloEvaluationService;
import com.roottrace.slo.SloRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandCenterServiceTest {

    private IncidentRepository incidentRepository;
    private SloRepository sloRepository;
    private SloEvaluationService sloEvaluationService;
    private ErrorBudgetService errorBudgetService;
    private ReliabilityTrendService reliabilityTrendService;
    private SreMetricsService sreMetricsService;
    private ServiceHealthService serviceHealthService;
    private ReliabilityScoreService reliabilityScoreService;
    private PostmortemActionItemRepository actionItemRepository;
    private RunbookExecutionRepository runbookExecutionRepository;
    private CommandCenterService commandCenterService;

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        sloRepository = mock(SloRepository.class);
        sloEvaluationService = mock(SloEvaluationService.class);
        errorBudgetService = mock(ErrorBudgetService.class);
        reliabilityTrendService = mock(ReliabilityTrendService.class);
        sreMetricsService = mock(SreMetricsService.class);
        serviceHealthService = mock(ServiceHealthService.class);
        reliabilityScoreService = mock(ReliabilityScoreService.class);
        actionItemRepository = mock(PostmortemActionItemRepository.class);
        runbookExecutionRepository = mock(RunbookExecutionRepository.class);

        commandCenterService = new CommandCenterService(
                incidentRepository,
                sloRepository,
                sloEvaluationService,
                errorBudgetService,
                reliabilityTrendService,
                sreMetricsService,
                serviceHealthService,
                reliabilityScoreService,
                actionItemRepository,
                runbookExecutionRepository
        );
    }

    @Test
    @DisplayName("Should aggregate command center overview with score, services, incidents, and SLOs")
    void testGetOverview_Success() {
        when(incidentRepository.findAllNotDeleted(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        SreMetricsSummaryResponse metrics = new SreMetricsSummaryResponse(
                30, 0L, 0L, 0L, 0.0, 0.0, 0.0, 0.0, 0L,
                java.util.Map.of(),
                List.of(), List.of()
        );
        when(sreMetricsService.getSreMetrics(30)).thenReturn(metrics);

        when(sloRepository.findAll()).thenReturn(List.of());
        when(actionItemRepository.findAll()).thenReturn(List.of());
        when(runbookExecutionRepository.findAll()).thenReturn(List.of());

        ServiceHealthSummaryResponse svcSummary = new ServiceHealthSummaryResponse(
                "payment-service", 90.0, "LOW", 0, 0, 0, 0.0, 0.0,
                0, 0, 0, 0, 0.0, 0, 0, 0, List.of()
        );
        when(serviceHealthService.getServiceHealthSummaries(30, 100, "risk")).thenReturn(List.of(svcSummary));

        ReliabilityScoreResponse scoreResp = new ReliabilityScoreResponse(
                100.0, 100.0, "LOW", List.of(new ReliabilityPenaltyResponse("NONE", 0.0, "Healthy"))
        );
        when(reliabilityScoreService.calculateReliabilityScore(anyInt(), anyInt(), anyDouble(), anyInt(), anyInt(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(scoreResp);

        CommandCenterOverviewResponse overview = commandCenterService.getOverview(30);

        assertThat(overview).isNotNull();
        assertThat(overview.windowDays()).isEqualTo(30);
        assertThat(overview.overallReliabilityScore()).isEqualTo(100.0);
        assertThat(overview.overallRiskTier()).isEqualTo("LOW");
        assertThat(overview.totalServices()).isEqualTo(1);
        assertThat(overview.servicesAtRisk()).isEqualTo(0);
    }
}

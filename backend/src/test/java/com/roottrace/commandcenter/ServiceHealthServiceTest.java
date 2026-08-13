package com.roottrace.commandcenter;

import com.roottrace.commandcenter.dto.ServiceHealthDetailResponse;
import com.roottrace.commandcenter.dto.ServiceHealthSummaryResponse;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.integration.RunbookExecutionRepository;
import com.roottrace.intelligence.SreMetricsService;
import com.roottrace.intelligence.ServiceRiskService;
import com.roottrace.postmortem.PostmortemActionItemRepository;
import com.roottrace.postmortem.PostmortemRepository;
import com.roottrace.slo.BurnRateService;
import com.roottrace.slo.ErrorBudgetService;
import com.roottrace.slo.ReliabilityRiskService;
import com.roottrace.slo.ReliabilityTrendService;
import com.roottrace.slo.Slo;
import com.roottrace.slo.SloEvaluationService;
import com.roottrace.slo.SloRepository;
import com.roottrace.slo.SloStatus;
import com.roottrace.slo.dto.BurnRateResponse;
import com.roottrace.slo.dto.ErrorBudgetResponse;
import com.roottrace.slo.dto.ReliabilityRiskResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServiceHealthServiceTest {

    private IncidentRepository incidentRepository;
    private SloRepository sloRepository;
    private SloEvaluationService sloEvaluationService;
    private ErrorBudgetService errorBudgetService;
    private BurnRateService burnRateService;
    private ReliabilityRiskService reliabilityRiskService;
    private ReliabilityTrendService reliabilityTrendService;
    private ServiceRiskService serviceRiskService;
    private SreMetricsService sreMetricsService;
    private PostmortemRepository postmortemRepository;
    private PostmortemActionItemRepository actionItemRepository;
    private RunbookExecutionRepository runbookExecutionRepository;
    private ReliabilityRecommendationService recommendationService;
    private ServiceHealthService serviceHealthService;

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        sloRepository = mock(SloRepository.class);
        sloEvaluationService = mock(SloEvaluationService.class);
        errorBudgetService = mock(ErrorBudgetService.class);
        burnRateService = mock(BurnRateService.class);
        reliabilityRiskService = mock(ReliabilityRiskService.class);
        reliabilityTrendService = mock(ReliabilityTrendService.class);
        serviceRiskService = mock(ServiceRiskService.class);
        sreMetricsService = mock(SreMetricsService.class);
        postmortemRepository = mock(PostmortemRepository.class);
        actionItemRepository = mock(PostmortemActionItemRepository.class);
        runbookExecutionRepository = mock(RunbookExecutionRepository.class);
        recommendationService = new ReliabilityRecommendationService();

        serviceHealthService = new ServiceHealthService(
                incidentRepository,
                sloRepository,
                sloEvaluationService,
                errorBudgetService,
                burnRateService,
                reliabilityRiskService,
                reliabilityTrendService,
                serviceRiskService,
                sreMetricsService,
                postmortemRepository,
                actionItemRepository,
                runbookExecutionRepository,
                recommendationService
        );
    }

    @Test
    @DisplayName("Should summarize health for all services sorted by risk")
    void testGetServiceHealthSummaries_Success() {
        Incident inc = mock(Incident.class);
        when(inc.getService()).thenReturn("payment-service");
        when(inc.getSeverity()).thenReturn(IncidentSeverity.HIGH);
        when(inc.getStatus()).thenReturn(IncidentStatus.RESOLVED);
        when(inc.getCreatedAt()).thenReturn(Instant.now().minusSeconds(3600));
        when(inc.getResolvedAt()).thenReturn(Instant.now().minusSeconds(1800));

        when(incidentRepository.findAllNotDeleted(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(inc)));

        Slo slo = mock(Slo.class);
        when(slo.getServiceName()).thenReturn("payment-service");
        when(slo.getEnabled()).thenReturn(true);
        when(sloRepository.findAll()).thenReturn(List.of(slo));
        when(sloRepository.findByServiceNameAndEnabledTrue("payment-service")).thenReturn(List.of(slo));

        SloEvaluationResponse eval = new SloEvaluationResponse(
                UUID.randomUUID(), "payment-service", "Payment Latency", 99.0, 99.5, 0.5,
                SloStatus.HEALTHY, 1000L, 995L, 5L, 50.0, 50.0, Instant.now()
        );
        when(sloEvaluationService.evaluateSlo(slo)).thenReturn(eval);

        ErrorBudgetResponse budget = new ErrorBudgetResponse(
                UUID.randomUUID(), "payment-service", "Payment Latency", 99.0, 1.0,
                1000L, 10L, 5L, 5L, 50.0, 50.0, SloStatus.HEALTHY
        );
        when(errorBudgetService.calculateErrorBudget(slo)).thenReturn(budget);

        ReliabilityRiskResponse risk = new ReliabilityRiskResponse(
                "payment-service", 25.0, "LOW", 0, 50.0, 0, 0, 0.0, 0, List.of(), Instant.now()
        );
        when(reliabilityRiskService.evaluateReliabilityRisk("payment-service")).thenReturn(risk);
        when(actionItemRepository.findAll()).thenReturn(List.of());
        when(runbookExecutionRepository.findAll()).thenReturn(List.of());

        List<ServiceHealthSummaryResponse> summaries = serviceHealthService.getServiceHealthSummaries(30, 10, "risk");

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).serviceName()).isEqualTo("payment-service");
        assertThat(summaries.get(0).healthScore()).isEqualTo(75.0); // 100 - 25 = 75
        assertThat(summaries.get(0).incidentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should return detailed service health including trends, action items, and recommendations")
    void testGetServiceHealthDetail_Success() {
        when(incidentRepository.findAllNotDeleted(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(sloRepository.findByServiceNameAndEnabledTrue("payment-service")).thenReturn(List.of());

        ReliabilityRiskResponse risk = new ReliabilityRiskResponse(
                "payment-service", 0.0, "LOW", 0, 0.0, 0, 0, 0.0, 0, List.of(), Instant.now()
        );
        when(reliabilityRiskService.evaluateReliabilityRisk("payment-service")).thenReturn(risk);
        when(postmortemRepository.findAll()).thenReturn(List.of());
        when(actionItemRepository.findAll()).thenReturn(List.of());
        when(runbookExecutionRepository.findAll()).thenReturn(List.of());

        ServiceHealthDetailResponse detail = serviceHealthService.getServiceHealthDetail("payment-service", 30);

        assertThat(detail).isNotNull();
        assertThat(detail.serviceName()).isEqualTo("payment-service");
        assertThat(detail.healthScore()).isEqualTo(100.0);
        assertThat(detail.riskTier()).isEqualTo("LOW");
        assertThat(detail.recommendations()).isNotEmpty();
    }
}

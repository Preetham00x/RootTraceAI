package com.roottrace.slo;

import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.postmortem.PostmortemActionItemRepository;
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

class ReliabilityRiskServiceTest {

    private SloRepository sloRepository;
    private SloEvaluationService sloEvaluationService;
    private ErrorBudgetService errorBudgetService;
    private BurnRateService burnRateService;
    private IncidentRepository incidentRepository;
    private PostmortemActionItemRepository actionItemRepository;
    private ReliabilityRiskService riskService;

    @BeforeEach
    void setUp() {
        sloRepository = mock(SloRepository.class);
        sloEvaluationService = mock(SloEvaluationService.class);
        errorBudgetService = mock(ErrorBudgetService.class);
        burnRateService = mock(BurnRateService.class);
        incidentRepository = mock(IncidentRepository.class);
        actionItemRepository = mock(PostmortemActionItemRepository.class);

        riskService = new ReliabilityRiskService(
                sloRepository,
                sloEvaluationService,
                errorBudgetService,
                burnRateService,
                incidentRepository,
                actionItemRepository
        );
    }

    @Test
    @DisplayName("Should evaluate LOW risk for healthy service with zero breaches")
    void testEvaluateReliabilityRisk_LowRisk() {
        when(sloRepository.findByServiceNameAndEnabledTrue("payment-service")).thenReturn(List.of());
        when(incidentRepository.findAllNotDeleted(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(actionItemRepository.findAll()).thenReturn(List.of());

        ReliabilityRiskResponse response = riskService.evaluateReliabilityRisk("payment-service");

        assertThat(response).isNotNull();
        assertThat(response.riskScore()).isEqualTo(0.0);
        assertThat(response.riskTier()).isEqualTo("LOW");
        assertThat(response.activeSloBreaches()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should evaluate CRITICAL risk when multiple SLO breaches and critical incidents exist")
    void testEvaluateReliabilityRisk_CriticalRisk() {
        Slo slo1 = mock(Slo.class);
        Slo slo2 = mock(Slo.class);
        when(sloRepository.findByServiceNameAndEnabledTrue("payment-service")).thenReturn(List.of(slo1, slo2));

        SloEvaluationResponse breachedEval = new SloEvaluationResponse(
                UUID.randomUUID(), "payment-service", "Availability", 99.9, 99.1, -0.8,
                SloStatus.BREACHED, 10000L, 9910L, 90L, 0.0, 100.0, Instant.now()
        );
        when(sloEvaluationService.evaluateSlo(any(Slo.class))).thenReturn(breachedEval);

        ErrorBudgetResponse budgetResp = new ErrorBudgetResponse(
                UUID.randomUUID(), "payment-service", "Availability", 99.9, 0.1, 10000L, 10L, 90L, 0L, 100.0, 0.0, SloStatus.BREACHED
        );
        when(errorBudgetService.calculateErrorBudget(any(Slo.class))).thenReturn(budgetResp);

        BurnRateResponse burnResp = new BurnRateResponse(
                UUID.randomUUID(), "payment-service", "Availability", 9.0, "CRITICAL", 60, 0.9, 0.1, Instant.now()
        );
        when(burnRateService.calculateBurnRate(any(Slo.class), eq(60))).thenReturn(burnResp);

        Incident crit1 = mock(Incident.class);
        when(crit1.getService()).thenReturn("payment-service");
        when(crit1.getSeverity()).thenReturn(IncidentSeverity.CRITICAL);
        when(crit1.getCreatedAt()).thenReturn(Instant.now().minusSeconds(3600));

        when(incidentRepository.findAllNotDeleted(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(crit1)));
        when(actionItemRepository.findAll()).thenReturn(List.of());

        ReliabilityRiskResponse response = riskService.evaluateReliabilityRisk("payment-service");

        assertThat(response.riskScore()).isGreaterThanOrEqualTo(80.0);
        assertThat(response.riskTier()).isEqualTo("CRITICAL");
        assertThat(response.activeSloBreaches()).isEqualTo(2);
        assertThat(response.riskFactors()).isNotEmpty();
    }

    @Test
    @DisplayName("Should evaluate MEDIUM risk when moderate budget consumed and unresolved action items exist")
    void testEvaluateReliabilityRisk_MediumRisk() {
        Slo slo1 = mock(Slo.class);
        when(sloRepository.findByServiceNameAndEnabledTrue("payment-service")).thenReturn(List.of(slo1));

        SloEvaluationResponse healthyEval = new SloEvaluationResponse(
                UUID.randomUUID(), "payment-service", "Availability", 99.9, 99.92, 0.02,
                SloStatus.HEALTHY, 10000L, 9992L, 8L, 20.0, 80.0, Instant.now()
        );
        when(sloEvaluationService.evaluateSlo(any(Slo.class))).thenReturn(healthyEval);

        ErrorBudgetResponse budgetResp = new ErrorBudgetResponse(
                UUID.randomUUID(), "payment-service", "Availability", 99.9, 0.1, 10000L, 10L, 8L, 2L, 80.0, 20.0, SloStatus.WARNING
        );
        when(errorBudgetService.calculateErrorBudget(any(Slo.class))).thenReturn(budgetResp);

        BurnRateResponse burnResp = new BurnRateResponse(
                UUID.randomUUID(), "payment-service", "Availability", 1.0, "ELEVATED", 60, 0.1, 0.1, Instant.now()
        );
        when(burnRateService.calculateBurnRate(any(Slo.class), eq(60))).thenReturn(burnResp);

        when(incidentRepository.findAllNotDeleted(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(actionItemRepository.findAll()).thenReturn(List.of());

        ReliabilityRiskResponse response = riskService.evaluateReliabilityRisk("payment-service");

        assertThat(response.riskScore()).isBetween(20.0, 50.0);
        assertThat(response.riskTier()).isIn("LOW", "MEDIUM");
    }

    @Test
    @DisplayName("Should clamp score strictly between 0 and 100")
    void testEvaluateReliabilityRisk_Clamping() {
        Slo slo1 = mock(Slo.class);
        Slo slo2 = mock(Slo.class);
        Slo slo3 = mock(Slo.class);
        Slo slo4 = mock(Slo.class);
        when(sloRepository.findByServiceNameAndEnabledTrue("payment-service")).thenReturn(List.of(slo1, slo2, slo3, slo4));

        SloEvaluationResponse breachedEval = new SloEvaluationResponse(
                UUID.randomUUID(), "payment-service", "Availability", 99.9, 95.0, -4.9,
                SloStatus.BREACHED, 10000L, 9500L, 500L, 0.0, 100.0, Instant.now()
        );
        when(sloEvaluationService.evaluateSlo(any(Slo.class))).thenReturn(breachedEval);

        ErrorBudgetResponse budgetResp = new ErrorBudgetResponse(
                UUID.randomUUID(), "payment-service", "Availability", 99.9, 0.1, 10000L, 10L, 500L, 0L, 100.0, 0.0, SloStatus.BREACHED
        );
        when(errorBudgetService.calculateErrorBudget(any(Slo.class))).thenReturn(budgetResp);

        BurnRateResponse burnResp = new BurnRateResponse(
                UUID.randomUUID(), "payment-service", "Availability", 20.0, "CRITICAL", 60, 2.0, 0.1, Instant.now()
        );
        when(burnRateService.calculateBurnRate(any(Slo.class), eq(60))).thenReturn(burnResp);

        when(incidentRepository.findAllNotDeleted(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(actionItemRepository.findAll()).thenReturn(List.of());

        ReliabilityRiskResponse response = riskService.evaluateReliabilityRisk("payment-service");

        assertThat(response.riskScore()).isLessThanOrEqualTo(100.0);
        assertThat(response.riskScore()).isGreaterThanOrEqualTo(0.0);
    }
}

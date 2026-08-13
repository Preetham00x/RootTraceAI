package com.roottrace.commandcenter;

import com.roottrace.commandcenter.dto.ReliabilityRecommendationResponse;
import com.roottrace.slo.SloStatus;
import com.roottrace.slo.dto.BurnRateResponse;
import com.roottrace.slo.dto.ErrorBudgetResponse;
import com.roottrace.slo.dto.SloEvaluationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReliabilityRecommendationServiceTest {

    private ReliabilityRecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        recommendationService = new ReliabilityRecommendationService();
    }

    @Test
    @DisplayName("Should generate healthy baseline recommendation when all metrics are normal")
    void testGenerateServiceRecommendations_Healthy() {
        List<ReliabilityRecommendationResponse> recs = recommendationService.generateServiceRecommendations(
                "payment-service", List.of(), List.of(), List.of(), 0, 0.0, 0, 0
        );

        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).type()).isEqualTo("MAINTAIN_HEALTH");
        assertThat(recs.get(0).priority()).isEqualTo("LOW");
    }

    @Test
    @DisplayName("Should generate CRITICAL recommendations for active SLO breaches")
    void testGenerateServiceRecommendations_SloBreach() {
        SloEvaluationResponse breached = new SloEvaluationResponse(
                UUID.randomUUID(), "payment-service", "Payment Latency", 99.0, 97.5, -1.5,
                SloStatus.BREACHED, 10000L, 9750L, 250L, 0.0, 100.0, Instant.now()
        );

        List<ReliabilityRecommendationResponse> recs = recommendationService.generateServiceRecommendations(
                "payment-service", List.of(breached), List.of(), List.of(), 0, 0.0, 0, 0
        );

        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).type()).isEqualTo("SLO_BREACH");
        assertThat(recs.get(0).priority()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("Should generate HIGH recommendations for high error budget consumption")
    void testGenerateServiceRecommendations_ErrorBudgetWarning() {
        ErrorBudgetResponse budget = new ErrorBudgetResponse(
                UUID.randomUUID(), "payment-service", "Payment Availability", 99.9, 0.1,
                10000L, 10L, 8L, 2L, 85.0, 15.0, SloStatus.WARNING
        );

        List<ReliabilityRecommendationResponse> recs = recommendationService.generateServiceRecommendations(
                "payment-service", List.of(), List.of(budget), List.of(), 0, 0.0, 0, 0
        );

        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).type()).isEqualTo("ERROR_BUDGET_WARNING");
        assertThat(recs.get(0).priority()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("Should generate recommendations for critical burn rate, overdue actions, and failed runbooks")
    void testGenerateServiceRecommendations_MultipleIssues() {
        BurnRateResponse burn = new BurnRateResponse(
                UUID.randomUUID(), "payment-service", "Payment Availability", 6.5, "CRITICAL", 60, 0.65, 0.1, Instant.now()
        );

        List<ReliabilityRecommendationResponse> recs = recommendationService.generateServiceRecommendations(
                "payment-service", List.of(), List.of(), List.of(burn), 2, 0.4, 3, 2
        );

        assertThat(recs).hasSize(4);
        assertThat(recs).extracting(ReliabilityRecommendationResponse::type)
                .containsExactlyInAnyOrder("BURN_RATE_CRITICAL", "OVERDUE_ACTIONS", "INCIDENT_RECURRENCE", "RUNBOOK_FAILURES");
    }
}

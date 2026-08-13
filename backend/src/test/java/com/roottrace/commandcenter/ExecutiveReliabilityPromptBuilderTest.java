package com.roottrace.commandcenter;

import com.roottrace.commandcenter.dto.CommandCenterOverviewResponse;
import com.roottrace.commandcenter.dto.ReliabilityPenaltyResponse;
import com.roottrace.commandcenter.dto.ReliabilityScoreResponse;
import com.roottrace.commandcenter.dto.ServiceHealthSummaryResponse;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.incident.dto.CreatorResponse;
import com.roottrace.incident.dto.IncidentSummaryResponse;
import com.roottrace.slo.SloStatus;
import com.roottrace.slo.dto.SloEvaluationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutiveReliabilityPromptBuilderTest {

    private ExecutiveReliabilityPromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        promptBuilder = new ExecutiveReliabilityPromptBuilder();
    }

    @Test
    @DisplayName("Should format grounded executive prompt with all operational sections")
    void testBuildPrompt_Success() {
        IncidentSummaryResponse inc = new IncidentSummaryResponse(
                UUID.randomUUID(), "Payment Gateway Down", "payment-service", IncidentSeverity.CRITICAL,
                IncidentStatus.OPEN, "prod", new CreatorResponse(UUID.randomUUID(), "Engineer"), Instant.now()
        );

        SloEvaluationResponse breach = new SloEvaluationResponse(
                UUID.randomUUID(), "payment-service", "Payment Latency", 99.0, 97.5, -1.5,
                SloStatus.BREACHED, 10000L, 9750L, 250L, 0.0, 100.0, Instant.now()
        );

        CommandCenterOverviewResponse overview = new CommandCenterOverviewResponse(
                30, 78.5, "MEDIUM", 8, 2, 12, 1, 11, 2, 4, 35.0, 12.0,
                15, 10, 2, 1, 45.0, 6, 2, 1,
                List.of(), List.of(inc), List.of(breach), List.of()
        );

        ReliabilityScoreResponse scoreResponse = new ReliabilityScoreResponse(
                78.5, 100.0, "MEDIUM",
                List.of(
                        new ReliabilityPenaltyResponse("SLO_BREACHES", 10.0, "2 active SLO breaches"),
                        new ReliabilityPenaltyResponse("ERROR_BUDGET", 8.0, "Average error budget consumed 45.0%")
                )
        );

        ServiceHealthSummaryResponse svc = new ServiceHealthSummaryResponse(
                "payment-service", 65.0, "HIGH", 5, 2, 1, 45.0, 0.4,
                2, 1, 1, 0, 80.0, 2, 1, 1, List.of("High Latency")
        );

        String prompt = promptBuilder.buildPrompt(overview, scoreResponse, List.of(svc));

        assertThat(prompt).isNotNull();
        assertThat(prompt).contains("Principal Executive SRE Advisor");
        assertThat(prompt).contains("78.5 / 100 (MEDIUM)");
        assertThat(prompt).contains("Payment Gateway Down");
        assertThat(prompt).contains("Payment Latency");
        assertThat(prompt).contains("payment-service");
    }
}

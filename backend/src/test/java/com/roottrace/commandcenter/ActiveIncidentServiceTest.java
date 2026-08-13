package com.roottrace.commandcenter;

import com.roottrace.commandcenter.dto.ActiveIncidentsResponse;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.slo.BurnRateService;
import com.roottrace.slo.ReliabilityRiskService;
import com.roottrace.slo.Slo;
import com.roottrace.slo.SloEvaluationService;
import com.roottrace.slo.SloRepository;
import com.roottrace.slo.SloStatus;
import com.roottrace.slo.dto.BurnRateResponse;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActiveIncidentServiceTest {

    private IncidentRepository incidentRepository;
    private SloRepository sloRepository;
    private SloEvaluationService sloEvaluationService;
    private BurnRateService burnRateService;
    private ReliabilityRiskService reliabilityRiskService;
    private ActiveIncidentService activeIncidentService;

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        sloRepository = mock(SloRepository.class);
        sloEvaluationService = mock(SloEvaluationService.class);
        burnRateService = mock(BurnRateService.class);
        reliabilityRiskService = mock(ReliabilityRiskService.class);

        activeIncidentService = new ActiveIncidentService(
                incidentRepository,
                sloRepository,
                sloEvaluationService,
                burnRateService,
                reliabilityRiskService
        );
    }

    @Test
    @DisplayName("Should prioritize active CRITICAL incidents with SLO breaches over MEDIUM incidents")
    void testGetActiveIncidents_Prioritization() {
        Incident critInc = mock(Incident.class);
        when(critInc.getId()).thenReturn(UUID.randomUUID());
        when(critInc.getTitle()).thenReturn("Database Outage");
        when(critInc.getService()).thenReturn("payment-service");
        when(critInc.getSeverity()).thenReturn(IncidentSeverity.CRITICAL);
        when(critInc.getStatus()).thenReturn(IncidentStatus.INVESTIGATING);
        when(critInc.getCreatedAt()).thenReturn(Instant.now().minusSeconds(3600));

        Incident medInc = mock(Incident.class);
        when(medInc.getId()).thenReturn(UUID.randomUUID());
        when(medInc.getTitle()).thenReturn("Minor latency");
        when(medInc.getService()).thenReturn("user-service");
        when(medInc.getSeverity()).thenReturn(IncidentSeverity.MEDIUM);
        when(medInc.getStatus()).thenReturn(IncidentStatus.OPEN);
        when(medInc.getCreatedAt()).thenReturn(Instant.now().minusSeconds(600));

        when(incidentRepository.findAllNotDeleted(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(medInc, critInc)));

        Slo slo = mock(Slo.class);
        when(sloRepository.findByServiceNameAndEnabledTrue("payment-service")).thenReturn(List.of(slo));
        when(sloRepository.findByServiceNameAndEnabledTrue("user-service")).thenReturn(List.of());

        SloEvaluationResponse eval = new SloEvaluationResponse(
                UUID.randomUUID(), "payment-service", "Availability", 99.9, 98.0, -1.9,
                SloStatus.BREACHED, 1000L, 980L, 20L, 0.0, 100.0, Instant.now()
        );
        when(sloEvaluationService.evaluateSlo(slo)).thenReturn(eval);

        BurnRateResponse burn = new BurnRateResponse(
                UUID.randomUUID(), "payment-service", "Availability", 5.5, "CRITICAL", 60, 0.55, 0.1, Instant.now()
        );
        when(burnRateService.calculateBurnRate(slo, 60)).thenReturn(burn);

        ReliabilityRiskResponse riskCrit = new ReliabilityRiskResponse(
                "payment-service", 85.0, "CRITICAL", 1, 95.0, 1, 2, 0.3, 2, List.of(), Instant.now()
        );
        when(reliabilityRiskService.evaluateReliabilityRisk("payment-service")).thenReturn(riskCrit);

        ReliabilityRiskResponse riskLow = new ReliabilityRiskResponse(
                "user-service", 10.0, "LOW", 0, 10.0, 0, 0, 0.0, 0, List.of(), Instant.now()
        );
        when(reliabilityRiskService.evaluateReliabilityRisk("user-service")).thenReturn(riskLow);

        ActiveIncidentsResponse response = activeIncidentService.getActiveIncidents(null, null, 10);

        assertThat(response).isNotNull();
        assertThat(response.totalActive()).isEqualTo(2);
        // Critical incident must be ranked #1 with IMMEDIATE attention
        assertThat(response.incidents().get(0).title()).isEqualTo("Database Outage");
        assertThat(response.incidents().get(0).recommendedAttention()).isEqualTo("IMMEDIATE");
        assertThat(response.incidents().get(0).priorityScore()).isGreaterThan(response.incidents().get(1).priorityScore());
    }
}

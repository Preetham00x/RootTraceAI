package com.roottrace.intelligence;

import com.roottrace.ai.diagnosis.AiDiagnosis;
import com.roottrace.ai.diagnosis.AiDiagnosisRepository;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.intelligence.dto.IncidentTrendsResponse;
import com.roottrace.intelligence.dto.SreMetricsSummaryResponse;
import com.roottrace.postmortem.Postmortem;
import com.roottrace.postmortem.PostmortemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SreMetricsServiceTest {

    private IncidentRepository incidentRepository;
    private AiDiagnosisRepository diagnosisRepository;
    private PostmortemRepository postmortemRepository;
    private SreMetricsService sreMetricsService;

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        diagnosisRepository = mock(AiDiagnosisRepository.class);
        postmortemRepository = mock(PostmortemRepository.class);

        sreMetricsService = new SreMetricsService(
                incidentRepository,
                diagnosisRepository,
                postmortemRepository
        );
    }

    @Test
    @DisplayName("Should compute MTTR, severity distribution, service breakdown, and exclude unresolved incidents")
    void testGetSreMetrics_Calculations() {
        UUID inc1Id = UUID.randomUUID();
        UUID inc2Id = UUID.randomUUID();
        UUID inc3Id = UUID.randomUUID();

        // 1. Resolved (30m)
        Incident inc1 = mock(Incident.class);
        when(inc1.getId()).thenReturn(inc1Id);
        when(inc1.getService()).thenReturn("payment-service");
        when(inc1.getSeverity()).thenReturn(IncidentSeverity.CRITICAL);
        when(inc1.getStatus()).thenReturn(IncidentStatus.RESOLVED);
        when(inc1.getCreatedAt()).thenReturn(Instant.now().minus(2, ChronoUnit.DAYS));
        when(inc1.getResolvedAt()).thenReturn(Instant.now().minus(2, ChronoUnit.DAYS).plus(30, ChronoUnit.MINUTES));

        // 2. Closed (60m)
        Incident inc2 = mock(Incident.class);
        when(inc2.getId()).thenReturn(inc2Id);
        when(inc2.getService()).thenReturn("payment-service");
        when(inc2.getSeverity()).thenReturn(IncidentSeverity.HIGH);
        when(inc2.getStatus()).thenReturn(IncidentStatus.CLOSED);
        when(inc2.getCreatedAt()).thenReturn(Instant.now().minus(5, ChronoUnit.DAYS));
        when(inc2.getResolvedAt()).thenReturn(Instant.now().minus(5, ChronoUnit.DAYS).plus(60, ChronoUnit.MINUTES));

        // 3. Open (unresolved - must NOT contribute to MTTR)
        Incident inc3 = mock(Incident.class);
        when(inc3.getId()).thenReturn(inc3Id);
        when(inc3.getService()).thenReturn("auth-service");
        when(inc3.getSeverity()).thenReturn(IncidentSeverity.LOW);
        when(inc3.getStatus()).thenReturn(IncidentStatus.INVESTIGATING);
        when(inc3.getCreatedAt()).thenReturn(Instant.now().minus(1, ChronoUnit.DAYS));
        when(inc3.getResolvedAt()).thenReturn(null);

        when(incidentRepository.findAllNotDeleted(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(inc1, inc2, inc3)));

        AiDiagnosis diag = mock(AiDiagnosis.class);
        when(diag.getProbableRootCause()).thenReturn("Database Connection Exhaustion");
        when(diagnosisRepository.findByIncidentIdOrderByCreatedAtDesc(inc1Id)).thenReturn(List.of(diag));
        when(diagnosisRepository.findByIncidentIdOrderByCreatedAtDesc(inc2Id)).thenReturn(List.of(diag));

        Postmortem pm1 = mock(Postmortem.class);
        when(pm1.getDowntimeMinutes()).thenReturn(30L);
        when(postmortemRepository.findByIncidentId(inc1Id)).thenReturn(Optional.of(pm1));

        Postmortem pm2 = mock(Postmortem.class);
        when(pm2.getDowntimeMinutes()).thenReturn(60L);
        when(postmortemRepository.findByIncidentId(inc2Id)).thenReturn(Optional.of(pm2));

        SreMetricsSummaryResponse metrics = sreMetricsService.getSreMetrics(30);

        assertThat(metrics).isNotNull();
        assertThat(metrics.totalIncidents()).isEqualTo(3);
        assertThat(metrics.resolvedIncidents()).isEqualTo(2);
        assertThat(metrics.activeIncidents()).isEqualTo(1);
        assertThat(metrics.meanTimeToResolveMinutes()).isEqualTo(45.0); // (30 + 60) / 2 = 45.0
        assertThat(metrics.medianTimeToResolveMinutes()).isEqualTo(45.0);
        assertThat(metrics.meanTimeToDetectMinutes()).isEqualTo(5.0);
        assertThat(metrics.totalDowntimeMinutes()).isEqualTo(90L);
        assertThat(metrics.severityCounts().get("CRITICAL")).isEqualTo(1);
        assertThat(metrics.severityCounts().get("HIGH")).isEqualTo(1);
        assertThat(metrics.severityCounts().get("LOW")).isEqualTo(1);
        assertThat(metrics.severityCounts().get("MEDIUM")).isEqualTo(0);
        assertThat(metrics.serviceBreakdown()).hasSize(2);
        assertThat(metrics.serviceBreakdown().get(0).service()).isEqualTo("payment-service");
        assertThat(metrics.serviceBreakdown().get(0).count()).isEqualTo(2);
        assertThat(metrics.topRecurringRootCauses()).hasSize(1);
        assertThat(metrics.topRecurringRootCauses().get(0).rootCause()).isEqualTo("Database Connection Exhaustion");
    }

    @Test
    @DisplayName("Should aggregate incident trends into daily and weekly data points")
    void testGetIncidentTrends_DailyAndWeekly() {
        Incident inc = mock(Incident.class);
        when(inc.getId()).thenReturn(UUID.randomUUID());
        when(inc.getSeverity()).thenReturn(IncidentSeverity.CRITICAL);
        when(inc.getStatus()).thenReturn(IncidentStatus.RESOLVED);
        when(inc.getCreatedAt()).thenReturn(Instant.now().minus(1, ChronoUnit.DAYS));
        when(inc.getResolvedAt()).thenReturn(Instant.now().minus(1, ChronoUnit.DAYS).plus(20, ChronoUnit.MINUTES));

        when(incidentRepository.findAllNotDeleted(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(inc)));

        IncidentTrendsResponse daily = sreMetricsService.getIncidentTrends(7, "daily");
        assertThat(daily.interval()).isEqualTo("daily");
        assertThat(daily.dataPoints()).isNotEmpty();

        IncidentTrendsResponse weekly = sreMetricsService.getIncidentTrends(30, "weekly");
        assertThat(weekly.interval()).isEqualTo("weekly");
        assertThat(weekly.dataPoints()).isNotEmpty();
    }

    @Test
    @DisplayName("Should throw BadRequestException for invalid days or interval")
    void testGetMetrics_Validation() {
        assertThatThrownBy(() -> sreMetricsService.getSreMetrics(0))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> sreMetricsService.getIncidentTrends(30, "hourly"))
                .isInstanceOf(BadRequestException.class);
    }
}

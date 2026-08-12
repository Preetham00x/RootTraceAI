package com.roottrace.intelligence;

import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.intelligence.dto.ServiceRiskResponse;
import com.roottrace.postmortem.ActionItemStatus;
import com.roottrace.postmortem.Postmortem;
import com.roottrace.postmortem.PostmortemActionItem;
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

class ServiceRiskServiceTest {

    private IncidentRepository incidentRepository;
    private PostmortemRepository postmortemRepository;
    private ServiceRiskService serviceRiskService;

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        postmortemRepository = mock(PostmortemRepository.class);
        serviceRiskService = new ServiceRiskService(incidentRepository, postmortemRepository);
    }

    @Test
    @DisplayName("Should evaluate high risk score and tier for a service with multiple critical incidents and open action items")
    void testEvaluateServiceRisk_HighRisk() {
        UUID inc1Id = UUID.randomUUID();
        UUID inc2Id = UUID.randomUUID();

        Incident inc1 = mock(Incident.class);
        when(inc1.getId()).thenReturn(inc1Id);
        when(inc1.getService()).thenReturn("payment-service");
        when(inc1.getSeverity()).thenReturn(IncidentSeverity.CRITICAL);
        when(inc1.getStatus()).thenReturn(IncidentStatus.OPEN);
        when(inc1.getCreatedAt()).thenReturn(Instant.now().minus(2, ChronoUnit.DAYS));

        Incident inc2 = mock(Incident.class);
        when(inc2.getId()).thenReturn(inc2Id);
        when(inc2.getService()).thenReturn("payment-service");
        when(inc2.getSeverity()).thenReturn(IncidentSeverity.CRITICAL);
        when(inc2.getStatus()).thenReturn(IncidentStatus.RESOLVED);
        when(inc2.getCreatedAt()).thenReturn(Instant.now().minus(5, ChronoUnit.DAYS));
        when(inc2.getResolvedAt()).thenReturn(Instant.now().minus(5, ChronoUnit.DAYS).plus(45, ChronoUnit.MINUTES));

        when(incidentRepository.findAllNotDeleted(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(inc1, inc2)));

        Postmortem pm = mock(Postmortem.class);
        PostmortemActionItem item1 = mock(PostmortemActionItem.class);
        when(item1.getStatus()).thenReturn(ActionItemStatus.OPEN);
        PostmortemActionItem item2 = mock(PostmortemActionItem.class);
        when(item2.getStatus()).thenReturn(ActionItemStatus.IN_PROGRESS);
        when(pm.getActionItems()).thenReturn(List.of(item1, item2));

        when(postmortemRepository.findByIncidentIdWithActionItems(inc2Id)).thenReturn(Optional.of(pm));

        ServiceRiskResponse risk = serviceRiskService.evaluateServiceRisk("payment-service");

        assertThat(risk).isNotNull();
        assertThat(risk.serviceName()).isEqualTo("payment-service");
        assertThat(risk.riskScore()).isGreaterThanOrEqualTo(50.0);
        assertThat(risk.riskTier()).isIn("HIGH", "CRITICAL");
        assertThat(risk.totalIncidents30d()).isEqualTo(2);
        assertThat(risk.criticalIncidents30d()).isEqualTo(2);
        assertThat(risk.openIncidentsCount()).isEqualTo(1);
        assertThat(risk.unresolvedActionItemsCount()).isEqualTo(2);
        assertThat(risk.riskFactors()).isNotEmpty();
    }

    @Test
    @DisplayName("Should evaluate low risk score and tier for a service with no incidents")
    void testEvaluateServiceRisk_LowRisk() {
        when(incidentRepository.findAllNotDeleted(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        ServiceRiskResponse risk = serviceRiskService.evaluateServiceRisk("reporting-service");

        assertThat(risk).isNotNull();
        assertThat(risk.riskScore()).isEqualTo(0.0);
        assertThat(risk.riskTier()).isEqualTo("LOW");
        assertThat(risk.riskFactors()).contains("No incidents recorded for service (0 pts)");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when serviceName is blank")
    void testEvaluateServiceRisk_BlankServiceName() {
        assertThatThrownBy(() -> serviceRiskService.evaluateServiceRisk("   "))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

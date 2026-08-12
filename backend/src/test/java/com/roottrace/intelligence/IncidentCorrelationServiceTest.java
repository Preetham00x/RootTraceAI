package com.roottrace.intelligence;

import com.roottrace.ai.diagnosis.SimilarIncidentService;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.incident.dto.SimilarIncidentResponse;
import com.roottrace.intelligence.dto.RelatedIncidentsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IncidentCorrelationServiceTest {

    private IncidentRepository incidentRepository;
    private SimilarIncidentService similarIncidentService;
    private IncidentCorrelationService correlationService;

    private final UUID targetIncidentId = UUID.randomUUID();
    private Incident targetIncident;

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        similarIncidentService = mock(SimilarIncidentService.class);
        correlationService = new IncidentCorrelationService(incidentRepository, similarIncidentService);

        targetIncident = mock(Incident.class);
        when(targetIncident.getId()).thenReturn(targetIncidentId);
        when(targetIncident.getTitle()).thenReturn("Payment Gateway Socket Timeout");
        when(targetIncident.getService()).thenReturn("payment-service");
        when(targetIncident.getSeverity()).thenReturn(IncidentSeverity.CRITICAL);
        when(targetIncident.getStatus()).thenReturn(IncidentStatus.OPEN);
        when(targetIncident.getCreatedAt()).thenReturn(Instant.parse("2026-08-12T12:00:00Z"));
        when(targetIncident.isDeleted()).thenReturn(false);

        when(incidentRepository.findById(targetIncidentId)).thenReturn(Optional.of(targetIncident));
    }

    @Test
    @DisplayName("Should calculate multi-criteria composite score combining semantic, service, and temporal proximity")
    void testFindRelatedIncidents_CompositeScoreCalculation() {
        UUID candidate1Id = UUID.randomUUID();
        UUID candidate2Id = UUID.randomUUID();

        SimilarIncidentResponse candidate1 = new SimilarIncidentResponse(
                candidate1Id,
                "Stripe API Timeout on Checkout",
                "payment-service",
                IncidentSeverity.CRITICAL,
                IncidentStatus.RESOLVED,
                "production",
                Instant.parse("2026-08-12T11:00:00Z"), // 1 hour prior -> temporal score 1.0
                Instant.parse("2026-08-12T11:30:00Z"),
                "Restarted payment pods",
                0.95 // 95% semantic similarity
        );

        SimilarIncidentResponse candidate2 = new SimilarIncidentResponse(
                candidate2Id,
                "Auth Token Validation Failure",
                "auth-service",
                IncidentSeverity.HIGH,
                IncidentStatus.RESOLVED,
                "production",
                Instant.parse("2026-07-15T12:00:00Z"), // > 20 days prior -> temporal score 0.15
                Instant.parse("2026-07-15T13:00:00Z"),
                "Updated JWT signing key",
                0.85 // 85% semantic similarity
        );

        when(similarIncidentService.findSimilar(eq(targetIncidentId), anyInt()))
                .thenReturn(List.of(candidate1, candidate2));

        RelatedIncidentsResponse response = correlationService.findRelatedIncidents(
                targetIncidentId, 5, 0.50, false
        );

        assertThat(response).isNotNull();
        assertThat(response.totalFound()).isEqualTo(2);
        assertThat(response.hasPotentialDuplicates()).isTrue();

        var top = response.relatedIncidents().get(0);
        assertThat(top.id()).isEqualTo(candidate1Id);
        assertThat(top.isSameService()).isTrue();
        assertThat(top.temporalDistanceHours()).isEqualTo(1.0);
        // composite = (0.95 * 0.60) + (1.0 * 0.25) + (1.0 * 0.15) = 0.57 + 0.25 + 0.15 = 0.97
        assertThat(top.compositeScore()).isGreaterThanOrEqualTo(0.95);
        assertThat(top.isDuplicateCandidate()).isTrue();
        assertThat(top.correlationReason()).contains("Potential duplicate");
    }

    @Test
    @DisplayName("Should filter by sameServiceOnly when flag is true")
    void testFindRelatedIncidents_SameServiceOnlyFilter() {
        UUID candidate1Id = UUID.randomUUID();
        UUID candidate2Id = UUID.randomUUID();

        SimilarIncidentResponse candidate1 = new SimilarIncidentResponse(
                candidate1Id, "Payment Error", "payment-service",
                IncidentSeverity.CRITICAL, IncidentStatus.RESOLVED, "production",
                Instant.parse("2026-08-11T12:00:00Z"), Instant.parse("2026-08-11T12:30:00Z"),
                "Fixed", 0.90
        );

        SimilarIncidentResponse candidate2 = new SimilarIncidentResponse(
                candidate2Id, "Cart Error", "cart-service",
                IncidentSeverity.HIGH, IncidentStatus.RESOLVED, "production",
                Instant.parse("2026-08-12T10:00:00Z"), Instant.parse("2026-08-12T10:30:00Z"),
                "Fixed", 0.85
        );

        when(similarIncidentService.findSimilar(eq(targetIncidentId), anyInt()))
                .thenReturn(List.of(candidate1, candidate2));

        RelatedIncidentsResponse response = correlationService.findRelatedIncidents(
                targetIncidentId, 5, 0.50, true
        );

        assertThat(response.totalFound()).isEqualTo(1);
        assertThat(response.relatedIncidents().get(0).service()).isEqualTo("payment-service");
    }

    @Test
    @DisplayName("Should throw BadRequestException for invalid limit or threshold parameters")
    void testFindRelatedIncidents_InvalidParameters() {
        assertThatThrownBy(() -> correlationService.findRelatedIncidents(targetIncidentId, 0, 0.60, false))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Limit must be between 1 and 20");

        assertThatThrownBy(() -> correlationService.findRelatedIncidents(targetIncidentId, 25, 0.60, false))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Limit must be between 1 and 20");

        assertThatThrownBy(() -> correlationService.findRelatedIncidents(targetIncidentId, 5, 1.5, false))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Threshold must be between 0.0 and 1.0");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when target incident is not found")
    void testFindRelatedIncidents_IncidentNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(incidentRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> correlationService.findRelatedIncidents(unknownId, 5, 0.60, false))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Incident");
    }
}

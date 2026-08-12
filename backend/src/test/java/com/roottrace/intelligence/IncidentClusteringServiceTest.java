package com.roottrace.intelligence;

import com.roottrace.ai.diagnosis.AiDiagnosis;
import com.roottrace.ai.diagnosis.AiDiagnosisRepository;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.intelligence.dto.IncidentClustersResponse;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IncidentClusteringServiceTest {

    private IncidentRepository incidentRepository;
    private AiDiagnosisRepository diagnosisRepository;
    private PostmortemRepository postmortemRepository;
    private IncidentClusteringService clusteringService;

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        diagnosisRepository = mock(AiDiagnosisRepository.class);
        postmortemRepository = mock(PostmortemRepository.class);

        clusteringService = new IncidentClusteringService(
                incidentRepository,
                diagnosisRepository,
                postmortemRepository
        );
    }

    @Test
    @DisplayName("Should group incidents into clusters by service and recurring failure mode")
    void testFindClusters_Grouping() {
        UUID inc1Id = UUID.randomUUID();
        UUID inc2Id = UUID.randomUUID();

        Incident inc1 = mock(Incident.class);
        when(inc1.getId()).thenReturn(inc1Id);
        when(inc1.getService()).thenReturn("payment-service");
        when(inc1.getTitle()).thenReturn("Hikari connection pool timeout");
        when(inc1.getStatus()).thenReturn(IncidentStatus.RESOLVED);
        when(inc1.getCreatedAt()).thenReturn(Instant.parse("2026-08-10T10:00:00Z"));
        when(inc1.getResolvedAt()).thenReturn(Instant.parse("2026-08-10T10:30:00Z"));

        Incident inc2 = mock(Incident.class);
        when(inc2.getId()).thenReturn(inc2Id);
        when(inc2.getService()).thenReturn("payment-service");
        when(inc2.getTitle()).thenReturn("Database socket connection pool exhausted");
        when(inc2.getStatus()).thenReturn(IncidentStatus.RESOLVED);
        when(inc2.getCreatedAt()).thenReturn(Instant.parse("2026-08-11T10:00:00Z"));
        when(inc2.getResolvedAt()).thenReturn(Instant.parse("2026-08-11T10:50:00Z"));

        when(incidentRepository.findAllNotDeleted(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(inc1, inc2)));

        AiDiagnosis diag = mock(AiDiagnosis.class);
        when(diag.getProbableRootCause()).thenReturn("Connection pool leak in worker threads");
        when(diagnosisRepository.findByIncidentIdOrderByCreatedAtDesc(inc1Id)).thenReturn(List.of(diag));
        when(diagnosisRepository.findByIncidentIdOrderByCreatedAtDesc(inc2Id)).thenReturn(List.of(diag));

        Postmortem pm = mock(Postmortem.class);
        PostmortemActionItem item = mock(PostmortemActionItem.class);
        when(item.getStatus()).thenReturn(ActionItemStatus.OPEN);
        when(pm.getActionItems()).thenReturn(List.of(item));
        when(postmortemRepository.findByIncidentIdWithActionItems(inc1Id)).thenReturn(Optional.of(pm));

        IncidentClustersResponse response = clusteringService.findClusters(null, 2);

        assertThat(response).isNotNull();
        assertThat(response.totalClusters()).isEqualTo(1);

        var cluster = response.clusters().get(0);
        assertThat(cluster.clusterId()).isEqualTo("cluster-payment-service-connection-pool-exhaustion");
        assertThat(cluster.service()).isEqualTo("payment-service");
        assertThat(cluster.incidentCount()).isEqualTo(2);
        assertThat(cluster.averageMttrMinutes()).isEqualTo(40.0); // (30 + 50) / 2 = 40.0
        assertThat(cluster.primaryRootCause()).contains("Connection pool leak");
        assertThat(cluster.hasOpenActionItems()).isTrue();
    }

    @Test
    @DisplayName("Should throw BadRequestException if minClusterSize is less than 1")
    void testFindClusters_InvalidMinClusterSize() {
        assertThatThrownBy(() -> clusteringService.findClusters("payment-service", 0))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("minClusterSize must be >= 1");
    }
}

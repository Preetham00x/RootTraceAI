package com.roottrace.intelligence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.common.exception.GlobalExceptionHandler;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.common.security.JwtAuthenticationFilter;
import com.roottrace.common.security.JwtService;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.intelligence.dto.CorrelatedIncidentResponse;
import com.roottrace.intelligence.dto.IncidentBriefingResponse;
import com.roottrace.intelligence.dto.IncidentClusterResponse;
import com.roottrace.intelligence.dto.IncidentClustersResponse;
import com.roottrace.intelligence.dto.IncidentTrendsResponse;
import com.roottrace.intelligence.dto.RelatedIncidentsResponse;
import com.roottrace.intelligence.dto.ServiceRiskResponse;
import com.roottrace.intelligence.dto.SreMetricsSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IntelligenceController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class IntelligenceControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IncidentCorrelationService correlationService;

    @MockitoBean
    private IncidentClusteringService clusteringService;

    @MockitoBean
    private SreMetricsService sreMetricsService;

    @MockitoBean
    private ServiceRiskService serviceRiskService;

    @MockitoBean
    private IncidentBriefingService incidentBriefingService;

    @MockitoBean
    private JwtService jwtService;

    private final UUID incidentId = UUID.randomUUID();

    @Test
    @DisplayName("Viewer can get related incidents (200 OK)")
    @WithMockUser(roles = "VIEWER")
    void testGetRelatedIncidents_Viewer_Success() throws Exception {
        RelatedIncidentsResponse response = new RelatedIncidentsResponse(
                incidentId,
                1,
                true,
                List.of(new CorrelatedIncidentResponse(
                        UUID.randomUUID(), "Past Outage", "payment-service",
                        IncidentSeverity.CRITICAL, IncidentStatus.RESOLVED,
                        Instant.now(), Instant.now(), "Restarted pods",
                        0.95, true, 2.5, 0.96, true, "Same service duplicate"
                ))
        );

        when(correlationService.findRelatedIncidents(eq(incidentId), any(), any(), any()))
                .thenReturn(response);

        mockMvc.perform(get("/api/incidents/{incidentId}/related", incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(incidentId.toString()))
                .andExpect(jsonPath("$.totalFound").value(1))
                .andExpect(jsonPath("$.hasPotentialDuplicates").value(true));
    }

    @Test
    @DisplayName("Viewer can get SRE incident intelligence briefing (200 OK)")
    @WithMockUser(roles = "VIEWER")
    void testGetIncidentIntelligence_Viewer_Success() throws Exception {
        IncidentBriefingResponse response = new IncidentBriefingResponse(
                incidentId,
                "Recurring issue in payment-service",
                true,
                2,
                List.of("Check DB connections"),
                List.of("Pool leak"),
                List.of("Run netstat"),
                List.of("Set alert threshold"),
                List.of("Open action item"),
                List.of(),
                Instant.now()
        );

        when(incidentBriefingService.generateBriefing(incidentId)).thenReturn(response);

        mockMvc.perform(get("/api/incidents/{incidentId}/intelligence", incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(incidentId.toString()))
                .andExpect(jsonPath("$.isRecurringIssue").value(true));
    }

    @Test
    @DisplayName("Viewer can get service risk score (200 OK)")
    @WithMockUser(roles = "VIEWER")
    void testGetServiceRisk_Viewer_Success() throws Exception {
        ServiceRiskResponse response = new ServiceRiskResponse(
                "payment-service",
                65.0,
                "HIGH",
                4,
                1,
                2,
                1,
                0.50,
                35.0,
                2,
                List.of("1 CRITICAL incident"),
                Instant.now()
        );

        when(serviceRiskService.evaluateServiceRisk("payment-service")).thenReturn(response);

        mockMvc.perform(get("/api/services/{serviceName}/risk", "payment-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName").value("payment-service"))
                .andExpect(jsonPath("$.riskScore").value(65.0))
                .andExpect(jsonPath("$.riskTier").value("HIGH"));
    }

    @Test
    @DisplayName("Viewer can get SRE metrics summary (200 OK)")
    @WithMockUser(roles = "VIEWER")
    void testGetSreMetrics_Viewer_Success() throws Exception {
        SreMetricsSummaryResponse response = new SreMetricsSummaryResponse(
                30, 10, 8, 2, 45.0, 35.0, 5.0, 0.30, 360L,
                Map.of("CRITICAL", 2L), List.of(), List.of()
        );

        when(sreMetricsService.getSreMetrics(30)).thenReturn(response);

        mockMvc.perform(get("/api/metrics/sre?days=30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncidents").value(10))
                .andExpect(jsonPath("$.meanTimeToResolveMinutes").value(45.0));
    }

    @Test
    @DisplayName("Viewer can get incident trends (200 OK)")
    @WithMockUser(roles = "VIEWER")
    void testGetIncidentTrends_Viewer_Success() throws Exception {
        IncidentTrendsResponse response = new IncidentTrendsResponse(
                30, "daily", List.of(new IncidentTrendsResponse.TrendDataPoint("2026-08-10", 2, 1, 1, 30.0))
        );

        when(sreMetricsService.getIncidentTrends(30, "daily")).thenReturn(response);

        mockMvc.perform(get("/api/metrics/incidents/trends?days=30&interval=daily"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interval").value("daily"))
                .andExpect(jsonPath("$.dataPoints[0].period").value("2026-08-10"));
    }

    @Test
    @DisplayName("Viewer can get incident clusters (200 OK)")
    @WithMockUser(roles = "VIEWER")
    void testGetIncidentClusters_Viewer_Success() throws Exception {
        IncidentClustersResponse response = new IncidentClustersResponse(
                1, List.of(new IncidentClusterResponse(
                "cluster-payment-service-db-pool", "payment-service", "Payment DB Pool",
                3, Instant.now(), 40.0, List.of(), "Pool leak", true
        ))
        );

        when(clusteringService.findClusters(null, 2)).thenReturn(response);

        mockMvc.perform(get("/api/incidents/clusters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClusters").value(1))
                .andExpect(jsonPath("$.clusters[0].clusterId").value("cluster-payment-service-db-pool"));
    }

    @Test
    @DisplayName("Should return 404 when incident is not found")
    @WithMockUser(roles = "ENGINEER")
    void testGetIncident_NotFound() throws Exception {
        when(correlationService.findRelatedIncidents(eq(incidentId), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Incident", incidentId));

        mockMvc.perform(get("/api/incidents/{incidentId}/related", incidentId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 400 when query parameter is invalid")
    @WithMockUser(roles = "ENGINEER")
    void testGetMetrics_BadRequest() throws Exception {
        when(sreMetricsService.getSreMetrics(0))
                .thenThrow(new BadRequestException("Days must be between 1 and 3650"));

        mockMvc.perform(get("/api/metrics/sre?days=0"))
                .andExpect(status().isBadRequest());
    }
}

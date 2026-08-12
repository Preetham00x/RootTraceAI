package com.roottrace.intelligence;

import com.roottrace.intelligence.dto.IncidentClustersResponse;
import com.roottrace.intelligence.dto.IncidentBriefingResponse;
import com.roottrace.intelligence.dto.IncidentTrendsResponse;
import com.roottrace.intelligence.dto.RelatedIncidentsResponse;
import com.roottrace.intelligence.dto.ServiceRiskResponse;
import com.roottrace.intelligence.dto.SreMetricsSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = "SRE Intelligence", description = "Multi-dimensional incident correlation, clustering, risk scoring, SRE metrics, and AI incident briefings")
public class IntelligenceController {

    private final IncidentCorrelationService correlationService;
    private final IncidentClusteringService clusteringService;
    private final SreMetricsService sreMetricsService;
    private final ServiceRiskService serviceRiskService;
    private final IncidentBriefingService incidentBriefingService;

    public IntelligenceController(
            IncidentCorrelationService correlationService,
            IncidentClusteringService clusteringService,
            SreMetricsService sreMetricsService,
            ServiceRiskService serviceRiskService,
            IncidentBriefingService incidentBriefingService) {
        this.correlationService = correlationService;
        this.clusteringService = clusteringService;
        this.sreMetricsService = sreMetricsService;
        this.serviceRiskService = serviceRiskService;
        this.incidentBriefingService = incidentBriefingService;
    }

    @GetMapping("/incidents/{incidentId}/related")
    @Operation(summary = "Find correlated historical incidents and identify duplicate candidates")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<RelatedIncidentsResponse> getRelatedIncidents(
            @PathVariable UUID incidentId,
            @RequestParam(required = false, defaultValue = "5") Integer limit,
            @RequestParam(required = false, defaultValue = "0.60") Double threshold,
            @RequestParam(required = false, defaultValue = "false") Boolean sameServiceOnly) {
        return ResponseEntity.ok(correlationService.findRelatedIncidents(incidentId, limit, threshold, sameServiceOnly));
    }

    @GetMapping("/incidents/{incidentId}/intelligence")
    @Operation(summary = "Generate an AI-assisted SRE incident intelligence briefing from historical data")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<IncidentBriefingResponse> getIncidentIntelligence(@PathVariable UUID incidentId) {
        return ResponseEntity.ok(incidentBriefingService.generateBriefing(incidentId));
    }

    @GetMapping("/services/{serviceName}/risk")
    @Operation(summary = "Calculate proactive service risk score and explainable risk factors")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<ServiceRiskResponse> getServiceRisk(@PathVariable String serviceName) {
        return ResponseEntity.ok(serviceRiskService.evaluateServiceRisk(serviceName));
    }

    @GetMapping("/metrics/sre")
    @Operation(summary = "Calculate organization-wide SRE operational metrics (MTTR, MTTD, recurrence rate, severity breakdown)")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<SreMetricsSummaryResponse> getSreMetrics(
            @RequestParam(required = false, defaultValue = "30") Integer days) {
        return ResponseEntity.ok(sreMetricsService.getSreMetrics(days));
    }

    @GetMapping("/metrics/incidents/trends")
    @Operation(summary = "Get historical incident frequency and MTTR trends aggregated by daily or weekly intervals")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<IncidentTrendsResponse> getIncidentTrends(
            @RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(required = false, defaultValue = "daily") String interval) {
        return ResponseEntity.ok(sreMetricsService.getIncidentTrends(days, interval));
    }

    @GetMapping("/incidents/clusters")
    @Operation(summary = "Discover recurring incident failure clusters grouped by service and root cause patterns")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<IncidentClustersResponse> getIncidentClusters(
            @RequestParam(required = false) String service,
            @RequestParam(required = false, defaultValue = "2") Integer minClusterSize) {
        return ResponseEntity.ok(clusteringService.findClusters(service, minClusterSize));
    }
}

package com.roottrace.slo;

import com.roottrace.slo.dto.ReliabilityAdvisorResponse;
import com.roottrace.slo.dto.ReliabilityDashboardResponse;
import com.roottrace.slo.dto.ReliabilityRiskResponse;
import com.roottrace.slo.dto.ReliabilityTrendResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/services/{serviceName}/reliability")
@Tag(name = "Reliability Governance", description = "Service reliability dashboards, proactive risk scoring, trends, and AI reliability advisor")
public class ReliabilityController {

    private final SloService sloService;
    private final ReliabilityRiskService reliabilityRiskService;
    private final ReliabilityTrendService reliabilityTrendService;
    private final ReliabilityAdvisorService reliabilityAdvisorService;

    public ReliabilityController(
            SloService sloService,
            ReliabilityRiskService reliabilityRiskService,
            ReliabilityTrendService reliabilityTrendService,
            ReliabilityAdvisorService reliabilityAdvisorService) {
        this.sloService = sloService;
        this.reliabilityRiskService = reliabilityRiskService;
        this.reliabilityTrendService = reliabilityTrendService;
        this.reliabilityAdvisorService = reliabilityAdvisorService;
    }

    @GetMapping
    @Operation(summary = "Get comprehensive reliability governance dashboard for a service")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<ReliabilityDashboardResponse> getReliabilityDashboard(
            @PathVariable String serviceName) {
        return ResponseEntity.ok(sloService.getReliabilityDashboard(serviceName));
    }

    @GetMapping("/risk")
    @Operation(summary = "Evaluate proactive 0–100 reliability risk score and explainable risk factors")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<ReliabilityRiskResponse> getReliabilityRisk(
            @PathVariable String serviceName) {
        return ResponseEntity.ok(reliabilityRiskService.evaluateReliabilityRisk(serviceName));
    }

    @GetMapping("/trends")
    @Operation(summary = "Get historical compliance and budget consumption trends")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<ReliabilityTrendResponse> getReliabilityTrends(
            @PathVariable String serviceName,
            @RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(required = false, defaultValue = "daily") String interval) {
        return ResponseEntity.ok(reliabilityTrendService.getReliabilityTrends(serviceName, days, interval));
    }

    @GetMapping("/advisor")
    @Operation(summary = "Generate AI-assisted reliability governance advisory report")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<ReliabilityAdvisorResponse> getReliabilityAdvisor(
            @PathVariable String serviceName) {
        return ResponseEntity.ok(reliabilityAdvisorService.generateReliabilityAdvice(serviceName));
    }
}

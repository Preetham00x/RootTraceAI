package com.roottrace.commandcenter;

import com.roottrace.commandcenter.dto.ActiveIncidentsResponse;
import com.roottrace.commandcenter.dto.CommandCenterOverviewResponse;
import com.roottrace.commandcenter.dto.ExecutiveReliabilityAdvisorResponse;
import com.roottrace.commandcenter.dto.IncidentCommandResponse;
import com.roottrace.commandcenter.dto.ReliabilityEventsResponse;
import com.roottrace.commandcenter.dto.ServiceHealthDetailResponse;
import com.roottrace.commandcenter.dto.ServiceHealthSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/command-center")
@Tag(name = "SRE Command Center", description = "Unified SRE Command Center, executive reliability metrics, service health, active incident prioritization, and AI advisor")
public class CommandCenterController {

    private final CommandCenterService commandCenterService;
    private final ServiceHealthService serviceHealthService;
    private final IncidentCommandService incidentCommandService;
    private final ActiveIncidentService activeIncidentService;
    private final ExecutiveReliabilityService executiveReliabilityService;
    private final ReliabilityEventFeedService reliabilityEventFeedService;

    public CommandCenterController(
            CommandCenterService commandCenterService,
            ServiceHealthService serviceHealthService,
            IncidentCommandService incidentCommandService,
            ActiveIncidentService activeIncidentService,
            ExecutiveReliabilityService executiveReliabilityService,
            ReliabilityEventFeedService reliabilityEventFeedService) {
        this.commandCenterService = commandCenterService;
        this.serviceHealthService = serviceHealthService;
        this.incidentCommandService = incidentCommandService;
        this.activeIncidentService = activeIncidentService;
        this.executiveReliabilityService = executiveReliabilityService;
        this.reliabilityEventFeedService = reliabilityEventFeedService;
    }

    @GetMapping("/overview")
    @Operation(summary = "Get aggregated organization reliability command center overview")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<CommandCenterOverviewResponse> getOverview(
            @RequestParam(required = false, defaultValue = "30") Integer days) {
        return ResponseEntity.ok(commandCenterService.getOverview(days));
    }

    @GetMapping("/services")
    @Operation(summary = "Get service-level reliability and health summaries")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<List<ServiceHealthSummaryResponse>> getServiceSummaries(
            @RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(required = false, defaultValue = "50") Integer limit,
            @RequestParam(required = false, defaultValue = "risk") String sort) {
        return ResponseEntity.ok(serviceHealthService.getServiceHealthSummaries(days, limit, sort));
    }

    @GetMapping("/services/{serviceName}")
    @Operation(summary = "Get comprehensive service reliability and health details")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<ServiceHealthDetailResponse> getServiceDetail(
            @PathVariable String serviceName,
            @RequestParam(required = false, defaultValue = "30") Integer days) {
        return ResponseEntity.ok(serviceHealthService.getServiceHealthDetail(serviceName, days));
    }

    @GetMapping("/incidents/{incidentId}")
    @Operation(summary = "Get unified incident command view consolidating diagnosis, plan, correlation, postmortem, and SLOs")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<IncidentCommandResponse> getIncidentCommand(
            @PathVariable UUID incidentId) {
        return ResponseEntity.ok(incidentCommandService.getIncidentCommandDetails(incidentId));
    }

    @GetMapping("/incidents/active")
    @Operation(summary = "Get active incidents sorted by deterministic operational urgency")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<ActiveIncidentsResponse> getActiveIncidents(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String service,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        return ResponseEntity.ok(activeIncidentService.getActiveIncidents(severity, service, limit));
    }

    @GetMapping("/advisor")
    @Operation(summary = "Generate Executive AI Reliability Advisor briefing report")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<ExecutiveReliabilityAdvisorResponse> getExecutiveAdvisor(
            @RequestParam(required = false, defaultValue = "30") Integer days) {
        return ResponseEntity.ok(executiveReliabilityService.generateExecutiveAdvisor(days));
    }

    @GetMapping("/events")
    @Operation(summary = "Get aggregated operational reliability event feed")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<ReliabilityEventsResponse> getEventFeed(
            @RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(required = false) String service,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        return ResponseEntity.ok(reliabilityEventFeedService.getEventFeed(service, days, limit));
    }
}

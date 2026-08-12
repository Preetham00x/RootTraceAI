package com.roottrace.integration;

import com.roottrace.integration.dto.CreateJiraTicketRequest;
import com.roottrace.integration.dto.JiraTicketResponse;
import com.roottrace.integration.dto.KubernetesPodStatus;
import com.roottrace.integration.dto.RunbookExecutionRequest;
import com.roottrace.integration.dto.RunbookExecutionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = "External Integrations", description = "Jira ticketing, controlled runbook executions, and Kubernetes operational inspection")
public class IntegrationController {

    private final JiraService jiraService;
    private final RunbookExecutionService runbookExecutionService;
    private final KubernetesService kubernetesService;

    public IntegrationController(
            JiraService jiraService,
            RunbookExecutionService runbookExecutionService,
            KubernetesService kubernetesService) {
        this.jiraService = jiraService;
        this.runbookExecutionService = runbookExecutionService;
        this.kubernetesService = kubernetesService;
    }

    @PostMapping("/incidents/{incidentId}/postmortem/action-items/{actionItemId}/jira")
    @Operation(summary = "Create a Jira issue for a postmortem action item")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ResponseEntity<JiraTicketResponse> createJiraTicket(
            @PathVariable UUID incidentId,
            @PathVariable UUID actionItemId,
            @RequestBody(required = false) CreateJiraTicketRequest request) {
        JiraTicketResponse response = jiraService.createJiraTicket(incidentId, actionItemId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/incidents/{incidentId}/runbooks/{stepId}/execute")
    @Operation(summary = "Request execution of a runbook step in controlled container environment")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ResponseEntity<RunbookExecutionResponse> requestRunbookExecution(
            @PathVariable UUID incidentId,
            @PathVariable UUID stepId,
            @RequestBody(required = false) RunbookExecutionRequest request) {
        RunbookExecutionResponse response = runbookExecutionService.requestExecution(incidentId, stepId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/incidents/{incidentId}/runbooks/{stepId}/approve")
    @Operation(summary = "Approve and execute a requested runbook command (Admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RunbookExecutionResponse> approveRunbookExecution(
            @PathVariable UUID incidentId,
            @PathVariable UUID stepId,
            @RequestParam(required = false) UUID executionId) {
        RunbookExecutionResponse response = runbookExecutionService.approveAndExecute(incidentId, stepId, executionId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/incidents/{incidentId}/runbooks/executions")
    @Operation(summary = "Get historical runbook executions for an incident")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<List<RunbookExecutionResponse>> getRunbookExecutions(
            @PathVariable UUID incidentId) {
        return ResponseEntity.ok(runbookExecutionService.getExecutions(incidentId));
    }

    @GetMapping("/integrations/kubernetes/pods")
    @Operation(summary = "Get active Kubernetes pod statuses for a service (Read-only)")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<List<KubernetesPodStatus.PodInfo>> getKubernetesPods(
            @RequestParam(required = false, defaultValue = "default") String namespace,
            @RequestParam(required = false, defaultValue = "payment-service") String service) {
        return ResponseEntity.ok(kubernetesService.getPods(namespace, service));
    }
}

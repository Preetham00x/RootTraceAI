package com.roottrace.investigation;

import com.roottrace.investigation.dto.CreateInvestigationPlanRequest;
import com.roottrace.investigation.dto.GenerateInvestigationPlanRequest;
import com.roottrace.investigation.dto.InvestigationPlanResponse;
import com.roottrace.investigation.dto.InvestigationStepResponse;
import com.roottrace.investigation.dto.UpdateInvestigationStepRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/incidents/{incidentId}/investigation-plans")
@Tag(name = "Investigation Plans", description = "Investigation plan generation, tracking, and remediation steps")
public class InvestigationController {

    private final InvestigationPlanService planService;

    public InvestigationController(InvestigationPlanService planService) {
        this.planService = planService;
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate a structured investigation plan from an AI diagnosis")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ResponseEntity<InvestigationPlanResponse> generatePlan(
            @PathVariable UUID incidentId,
            @Valid @RequestBody GenerateInvestigationPlanRequest request) {
        InvestigationPlanResponse response = planService.generatePlan(incidentId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping
    @Operation(summary = "Manually create an investigation plan for an incident")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ResponseEntity<InvestigationPlanResponse> createPlan(
            @PathVariable UUID incidentId,
            @Valid @RequestBody CreateInvestigationPlanRequest request) {
        InvestigationPlanResponse response = planService.createPlan(incidentId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List all investigation plans for an incident")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<List<InvestigationPlanResponse>> getPlans(@PathVariable UUID incidentId) {
        return ResponseEntity.ok(planService.getPlans(incidentId));
    }

    @PatchMapping("/{planId}/steps/{stepId}")
    @Operation(summary = "Update an investigation step status, evidence notes, or assigned user")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ResponseEntity<InvestigationStepResponse> updateStep(
            @PathVariable UUID incidentId,
            @PathVariable UUID planId,
            @PathVariable UUID stepId,
            @Valid @RequestBody UpdateInvestigationStepRequest request) {
        return ResponseEntity.ok(planService.updateStep(incidentId, planId, stepId, request));
    }
}

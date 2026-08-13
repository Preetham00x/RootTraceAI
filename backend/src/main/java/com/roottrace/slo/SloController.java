package com.roottrace.slo;

import com.roottrace.common.security.CurrentUserService;
import com.roottrace.slo.dto.BurnRateResponse;
import com.roottrace.slo.dto.CreateSloRequest;
import com.roottrace.slo.dto.ErrorBudgetResponse;
import com.roottrace.slo.dto.RecordSliMeasurementRequest;
import com.roottrace.slo.dto.SliMeasurementResponse;
import com.roottrace.slo.dto.SloEvaluationResponse;
import com.roottrace.slo.dto.SloResponse;
import com.roottrace.slo.dto.UpdateSloRequest;
import com.roottrace.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/services/{serviceName}/slos")
@Tag(name = "SLO Management", description = "Service Level Objectives, SLI measurements, error budgets, and compliance evaluation")
public class SloController {

    private final SloService sloService;
    private final SloEvaluationService sloEvaluationService;
    private final ErrorBudgetService errorBudgetService;
    private final BurnRateService burnRateService;
    private final CurrentUserService currentUserService;

    public SloController(
            SloService sloService,
            SloEvaluationService sloEvaluationService,
            ErrorBudgetService errorBudgetService,
            BurnRateService burnRateService,
            CurrentUserService currentUserService) {
        this.sloService = sloService;
        this.sloEvaluationService = sloEvaluationService;
        this.errorBudgetService = errorBudgetService;
        this.burnRateService = burnRateService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @Operation(summary = "Create a new SLO for a service")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ResponseEntity<SloResponse> createSlo(
            @PathVariable String serviceName,
            @Valid @RequestBody CreateSloRequest request) {
        SloResponse response = sloService.createSlo(serviceName, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List all SLOs for a service")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<List<SloResponse>> listSlos(
            @PathVariable String serviceName) {
        return ResponseEntity.ok(sloService.listSlos(serviceName));
    }

    @GetMapping("/{sloId}")
    @Operation(summary = "Get SLO details by ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<SloResponse> getSlo(
            @PathVariable String serviceName,
            @PathVariable UUID sloId) {
        return ResponseEntity.ok(sloService.getSlo(serviceName, sloId));
    }

    @PatchMapping("/{sloId}")
    @Operation(summary = "Update an existing SLO")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ResponseEntity<SloResponse> updateSlo(
            @PathVariable String serviceName,
            @PathVariable UUID sloId,
            @Valid @RequestBody UpdateSloRequest request) {
        return ResponseEntity.ok(sloService.updateSlo(serviceName, sloId, request));
    }

    @DeleteMapping("/{sloId}")
    @Operation(summary = "Disable an SLO (soft delete, Admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> disableSlo(
            @PathVariable String serviceName,
            @PathVariable UUID sloId) {
        sloService.disableSlo(serviceName, sloId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{sloId}/measurements")
    @Operation(summary = "Record SLI measurement events for an SLO")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ResponseEntity<SliMeasurementResponse> recordMeasurement(
            @PathVariable String serviceName,
            @PathVariable UUID sloId,
            @Valid @RequestBody RecordSliMeasurementRequest request) {
        SliMeasurementResponse response = sloService.recordMeasurement(serviceName, sloId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{sloId}/evaluate")
    @Operation(summary = "Evaluate SLO compliance and trigger breach detection audit if violated")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ResponseEntity<SloEvaluationResponse> evaluateAndDetectBreach(
            @PathVariable String serviceName,
            @PathVariable UUID sloId) {
        User currentUser = currentUserService.getCurrentUser();
        String caller = (currentUser != null) ? currentUser.getEmail() : "system";
        return ResponseEntity.ok(sloEvaluationService.evaluateAndDetectBreach(sloId, caller));
    }

    @GetMapping("/{sloId}/evaluation")
    @Operation(summary = "Get current SLO compliance evaluation")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<SloEvaluationResponse> getEvaluation(
            @PathVariable String serviceName,
            @PathVariable UUID sloId) {
        return ResponseEntity.ok(sloEvaluationService.evaluateSlo(sloId));
    }

    @GetMapping("/{sloId}/error-budget")
    @Operation(summary = "Calculate remaining and consumed error budget for an SLO")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<ErrorBudgetResponse> getErrorBudget(
            @PathVariable String serviceName,
            @PathVariable UUID sloId) {
        return ResponseEntity.ok(errorBudgetService.calculateErrorBudget(sloId));
    }

    @GetMapping("/{sloId}/burn-rate")
    @Operation(summary = "Calculate real-time error budget burn rate over time window")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<BurnRateResponse> getBurnRate(
            @PathVariable String serviceName,
            @PathVariable UUID sloId,
            @RequestParam(required = false, defaultValue = "60") Integer windowMinutes) {
        return ResponseEntity.ok(burnRateService.calculateBurnRate(sloId, windowMinutes));
    }
}

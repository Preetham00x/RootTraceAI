package com.roottrace.ai.diagnosis.controller;

import com.roottrace.ai.diagnosis.DiagnosisService;
import com.roottrace.ai.diagnosis.FeedbackService;
import com.roottrace.ai.diagnosis.dto.AiMetricsResponse;
import com.roottrace.ai.diagnosis.dto.DiagnosisDetailResponse;
import com.roottrace.ai.diagnosis.dto.DiagnosisResultResponse;
import com.roottrace.ai.diagnosis.dto.DiagnosisSummaryResponse;
import com.roottrace.ai.diagnosis.dto.FeedbackRequest;
import com.roottrace.ai.diagnosis.dto.FeedbackResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = "AI Diagnosis", description = "AI-powered incident diagnosis and feedback")
public class DiagnosisController {

    private final DiagnosisService diagnosisService;
    private final FeedbackService feedbackService;

    public DiagnosisController(DiagnosisService diagnosisService, FeedbackService feedbackService) {
        this.diagnosisService = diagnosisService;
        this.feedbackService = feedbackService;
    }

    @PostMapping("/incidents/{incidentId}/diagnose")
    @Operation(summary = "Generate a new AI diagnosis for an incident")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ResponseEntity<DiagnosisResultResponse> diagnose(@PathVariable UUID incidentId) {
        DiagnosisResultResponse response = diagnosisService.diagnose(incidentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/incidents/{incidentId}/diagnoses")
    @Operation(summary = "List all AI diagnoses for an incident")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<List<DiagnosisSummaryResponse>> getDiagnoses(@PathVariable UUID incidentId) {
        return ResponseEntity.ok(diagnosisService.getDiagnoses(incidentId));
    }

    @GetMapping("/incidents/{incidentId}/diagnoses/{diagnosisId}")
    @Operation(summary = "Get full details of a specific AI diagnosis")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'VIEWER')")
    public ResponseEntity<DiagnosisDetailResponse> getDiagnosis(
            @PathVariable UUID incidentId,
            @PathVariable UUID diagnosisId) {
        return ResponseEntity.ok(diagnosisService.getDiagnosis(incidentId, diagnosisId));
    }

    @PostMapping("/incidents/{incidentId}/diagnoses/{diagnosisId}/feedback")
    @Operation(summary = "Submit helpful/unhelpful feedback on a diagnosis")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ResponseEntity<FeedbackResponse> submitFeedback(
            @PathVariable UUID incidentId,
            @PathVariable UUID diagnosisId,
            @Valid @RequestBody FeedbackRequest request) {
        FeedbackResponse response = feedbackService.submitFeedback(incidentId, diagnosisId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/ai/metrics")
    @Operation(summary = "Get system-wide AI helpfulness metrics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AiMetricsResponse> getMetrics() {
        return ResponseEntity.ok(feedbackService.getMetrics());
    }
}
